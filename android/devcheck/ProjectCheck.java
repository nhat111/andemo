import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Soát các bước hay bị quên khi thêm màn hình / adapter, CHỈ đọc file text: không cần Gradle, thư viện, emulator.
 * Hợp với VDI không internet: code "đỏ" vì thiếu thư viện nên không phân biệt được lỗi thật, tool này thì vẫn chạy.
 *
 * Chạy từ thư mục android/ (Java 11+, chạy thẳng file .java, không cần javac):
 *   java devcheck/ProjectCheck.java                      (mặc định: app/src/main)
 *   java devcheck/ProjectCheck.java đường\dẫn\module\src\main
 *
 * ERROR = chắc chắn hỏng (build lỗi hoặc crash khi chạy). WARN = nên xem lại.
 * Có ERROR → mã thoát 1. Hướng dẫn: docs/nexacro-migration/CHECKLISTS.md
 */
public class ProjectCheck {

    private static final List<String> errors = new ArrayList<>();
    private static final List<String> warns = new ArrayList<>();

    // R.xxx.yyy của app (bỏ android.R.xxx: trước chữ R không được là chữ / dấu chấm)
    private static final Pattern R_REF = Pattern.compile("(?<![\\w.])R\\.(layout|id|string|color|dimen|drawable|mipmap)\\.(\\w+)");
    private static final Pattern XML_REF = Pattern.compile("@(layout|id|string|color|dimen|drawable|mipmap)/(\\w+)");
    private static final Pattern ID_DEF = Pattern.compile("@\\+id/(\\w+)");
    private static final Pattern VALUE_DEF = Pattern.compile("<(string|color|dimen|item)\\s+name=\"(\\w+)\"");
    private static final Pattern INCLUDE = Pattern.compile("<include[^>]*layout=\"@layout/(\\w+)\"");
    private static final Pattern VIEW_LOOKUP = Pattern.compile("(?:findViewById|\\.text|\\.view)\\(\\s*R\\.id\\.(\\w+)");
    private static final Pattern ACTIVITY_CLASS = Pattern.compile("public\\s+class\\s+(\\w+)\\s+extends\\s+\\w*Activity\\b");
    private static final Pattern MANIFEST_ACTIVITY = Pattern.compile("<activity[^>]*android:name=\"([\\w.]+)\"", Pattern.DOTALL);
    private static final Pattern PACKAGE = Pattern.compile("^package\\s+([\\w.]+);", Pattern.MULTILINE);
    // Comment Java (/* */ và //, bỏ qua "//" trong chuỗi như "https://") và comment XML
    private static final Pattern COMMENT = Pattern.compile("/\\*.*?\\*/|(?<![:\"])//[^\\n]*", Pattern.DOTALL);
    private static final Pattern XML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern RES_VALUE = Pattern.compile("resValue\\s*\\(?\\s*[\"'](\\w+)[\"']\\s*,\\s*[\"'](\\w+)[\"']");
    private static final Pattern INFLATE_2_ARGS = Pattern.compile("inflate\\(\\s*R\\.layout\\.\\w+\\s*,\\s*[^,()]+\\)");

    public static void main(String[] args) throws IOException {
        Path main = Paths.get(args.length > 0 ? args[0] : "app/src/main");
        Path java = main.resolve("java");
        Path res = main.resolve("res");
        Path manifest = main.resolve("AndroidManifest.xml");
        if (!Files.isDirectory(java) || !Files.isDirectory(res)) {
            System.out.println("Không thấy " + java + " / " + res + ". Chạy từ thư mục android/ hoặc truyền đường dẫn src/main.");
            System.exit(2);
        }

        // ---- Thu thập tài nguyên đang có ----
        Map<String, Set<String>> defined = new HashMap<>();
        for (String t : new String[]{"layout", "id", "string", "color", "dimen", "drawable", "mipmap"}) {
            defined.put(t, new HashSet<>());
        }
        Map<String, Set<String>> idsInLayout = new HashMap<>();
        Map<String, Set<String>> includesOfLayout = new HashMap<>();
        List<Path> resXml = list(res, ".xml");
        for (Path dir : listDirs(res)) {
            String type = dir.getFileName().toString().split("-")[0];
            if (type.equals("layout") || type.equals("drawable") || type.equals("mipmap")) {
                for (Path f : list(dir, "")) {
                    defined.get(type).add(baseName(f));
                }
            }
        }
        for (Path f : resXml) {
            String s = read(f);
            defined.get("id").addAll(all(ID_DEF, s, 1));
            Matcher m = VALUE_DEF.matcher(s);
            while (m.find()) {
                String kind = m.group(1);
                if (kind.equals("item")) {
                    defined.get("id").add(m.group(2)); // <item type="id" name="..."/>
                } else {
                    defined.get(kind).add(m.group(2));
                }
            }
            if (f.getParent().getFileName().toString().startsWith("layout")) {
                idsInLayout.computeIfAbsent(baseName(f), k -> new HashSet<>()).addAll(all(ID_DEF, s, 1));
                includesOfLayout.computeIfAbsent(baseName(f), k -> new HashSet<>()).addAll(all(INCLUDE, s, 1));
            }
        }

        // resValue "string", "app_name", "..." trong build.gradle cũng sinh ra tài nguyên
        Path gradle = main.getParent() == null ? null : main.getParent().getParent();
        for (String g : new String[]{"build.gradle", "build.gradle.kts"}) {
            if (gradle != null && Files.exists(gradle.resolve(g))) {
                Matcher m = RES_VALUE.matcher(read(gradle.resolve(g)));
                while (m.find()) {
                    if (defined.containsKey(m.group(1))) {
                        defined.get(m.group(1)).add(m.group(2));
                    }
                }
            }
        }

        // ---- 1. Activity <-> AndroidManifest ----
        String manifestText = Files.exists(manifest) ? read(manifest) : "";
        String appPackage = attr(manifestText, "package");
        Set<String> declared = new HashSet<>();
        Matcher ma = MANIFEST_ACTIVITY.matcher(manifestText);
        while (ma.find()) {
            declared.add(ma.group(1));
        }
        List<Path> javaFiles = list(java, ".java");
        Set<String> activityClasses = new HashSet<>();
        for (Path f : javaFiles) {
            String s = read(f);
            Matcher ac = ACTIVITY_CLASS.matcher(s);
            if (ac.find()) {
                String pkg = first(PACKAGE, s);
                String full = pkg + "." + ac.group(1);
                activityClasses.add(full);
                if (!isDeclared(full, declared, appPackage, f, java)) {
                    error(f, lineOf(s, ac.start()), "Activity " + ac.group(1)
                            + " chưa khai báo trong AndroidManifest.xml → mở màn hình sẽ crash (ActivityNotFoundException)");
                }
            }
        }
        for (String name : declared) {
            String full = name.startsWith(".") ? guessPackage(javaFiles, java) + name : name;
            boolean own = name.startsWith(".") || !name.contains(".") || full.startsWith(guessPackage(javaFiles, java));
            if (own && !name.contains("$") && javaFiles.stream().noneMatch(p -> className(java, p).equals(full))) {
                warns.add("AndroidManifest.xml: khai báo " + name + " nhưng không tìm thấy class (đã đổi tên / xoá?)");
            }
        }

        // ---- 2. Tham chiếu R.xxx trong Java, @xxx/ trong XML ----
        for (Path f : javaFiles) {
            String s = read(f);
            Matcher m = R_REF.matcher(s);
            while (m.find()) {
                if (!defined.get(m.group(1)).contains(m.group(2))) {
                    error(f, lineOf(s, m.start()), "R." + m.group(1) + "." + m.group(2) + " không tồn tại trong res/ → lỗi build");
                }
            }
        }
        List<Path> xmlToScan = new ArrayList<>(resXml);
        if (Files.exists(manifest)) {
            xmlToScan.add(manifest);
        }
        for (Path f : xmlToScan) {
            String s = read(f);
            Matcher m = XML_REF.matcher(s);
            while (m.find()) {
                if (s.startsWith("+", m.start() + 1)) {
                    continue;
                }
                if (!defined.get(m.group(1)).contains(m.group(2))) {
                    error(f, lineOf(s, m.start()), "@" + m.group(1) + "/" + m.group(2) + " không tồn tại → lỗi build");
                }
            }
        }

        // ---- 3. findViewById(R.id.x) phải có trong layout mà file đó dùng ----
        for (Path f : javaFiles) {
            String s = read(f);
            Set<String> layouts = new TreeSet<>();
            Matcher lm = Pattern.compile("(?<![\\w.])R\\.layout\\.(\\w+)").matcher(s);
            while (lm.find()) {
                layouts.add(lm.group(1));
            }
            layouts.retainAll(defined.get("layout")); // layout sai tên đã báo ở bước 2, không báo dây chuyền
            if (layouts.isEmpty()) {
                continue; // không biết view lấy từ layout nào: bỏ qua
            }
            Set<String> ids = new HashSet<>();
            for (String l : layouts) {
                collectIds(l, idsInLayout, includesOfLayout, ids, new HashSet<>());
            }
            Matcher vm = VIEW_LOOKUP.matcher(s);
            while (vm.find()) {
                String id = vm.group(1);
                if (defined.get("id").contains(id) && !ids.contains(id)) {
                    error(f, lineOf(s, vm.start()), "id " + id + " không có trong layout " + layouts
                            + " → findViewById trả null → NullPointerException khi chạy");
                }
            }
        }

        // ---- 4. Lỗi hay gặp ----
        for (Path f : javaFiles) {
            String s = read(f);
            int i = s.indexOf("case R.id.");
            if (i >= 0) {
                error(f, lineOf(s, i), "switch / case R.id.x: từ AGP 8 R.id không còn là hằng số → lỗi build. Dùng if / else"
                        + " (trừ khi gradle.properties có android.nonFinalResIds=false)");
            }
            Matcher im = INFLATE_2_ARGS.matcher(s);
            while (im.find()) {
                warn(f, lineOf(s, im.start()), "inflate 2 tham số: trong adapter dùng inflate(layout, parent, false)"
                        + " (null làm mất layout_height của dòng, parent không có false thì crash)");
            }
            if (s.contains("extends BaseAdapter") && s.contains("getView(") && !s.contains("convertView == null")) {
                warn(f, lineOf(s, s.indexOf("getView(")), "getView không kiểm tra convertView == null: inflate mỗi lần cuộn, chậm");
            }
            if (s.contains("extends BaseAdapter") || s.contains("extends HashMapListAdapter")) {
                if ((s.contains("View.GONE") || s.contains("View.INVISIBLE")) && !s.contains("View.VISIBLE")) {
                    warn(f, lineOf(s, s.indexOf("View.GONE") >= 0 ? s.indexOf("View.GONE") : s.indexOf("View.INVISIBLE")),
                            "adapter có ẩn view mà không chỗ nào hiện lại (View.VISIBLE): view dùng lại sẽ bị ẩn sai dòng");
                }
            }
        }

        // ---- Kết quả ----
        System.out.println("Soát " + javaFiles.size() + " file Java, " + resXml.size() + " file XML, "
                + activityClasses.size() + " Activity");
        System.out.println();
        errors.forEach(e -> System.out.println("ERROR " + e));
        warns.forEach(w -> System.out.println("WARN  " + w));
        System.out.println();
        System.out.println(errors.size() + " ERROR, " + warns.size() + " WARN");
        if (!errors.isEmpty()) {
            System.exit(1);
        }
    }

    private static boolean isDeclared(String full, Set<String> declared, String appPackage, Path file, Path javaRoot) {
        for (String d : declared) {
            if (d.equals(full)) {
                return true;
            }
            if (d.startsWith(".") && full.endsWith(d)) {
                return true; // ".plain.X": tương đối theo namespace / package của app
            }
            if (!d.contains(".") && full.endsWith("." + d)) {
                return true;
            }
        }
        return false;
    }

    /** Package gốc = package ngắn nhất có Activity (đủ cho ".X" trong manifest) */
    private static String guessPackage(List<Path> javaFiles, Path javaRoot) {
        return javaFiles.stream()
                .map(p -> className(javaRoot, p))
                .map(c -> c.substring(0, Math.max(0, c.lastIndexOf('.'))))
                .min((a, b) -> a.length() - b.length())
                .orElse("");
    }

    private static void collectIds(String layout, Map<String, Set<String>> ids, Map<String, Set<String>> includes,
                                   Set<String> out, Set<String> seen) {
        if (!seen.add(layout)) {
            return;
        }
        out.addAll(ids.getOrDefault(layout, Set.of()));
        for (String inc : includes.getOrDefault(layout, Set.of())) {
            collectIds(inc, ids, includes, out, seen);
        }
    }

    private static String className(Path javaRoot, Path file) {
        String rel = javaRoot.relativize(file).toString().replace('\\', '/').replace('/', '.');
        return rel.substring(0, rel.length() - ".java".length());
    }

    private static void error(Path f, int line, String msg) {
        errors.add(f.toString().replace('\\', '/') + ":" + line + "  " + msg);
    }

    private static void warn(Path f, int line, String msg) {
        warns.add(f.toString().replace('\\', '/') + ":" + line + "  " + msg);
    }

    private static int lineOf(String s, int index) {
        int line = 1;
        for (int i = 0; i < index && i < s.length(); i++) {
            if (s.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static Set<String> all(Pattern p, String s, int group) {
        Set<String> out = new HashSet<>();
        Matcher m = p.matcher(s);
        while (m.find()) {
            out.add(m.group(group));
        }
        return out;
    }

    private static String first(Pattern p, String s) {
        Matcher m = p.matcher(s);
        return m.find() ? m.group(1) : "";
    }

    private static String attr(String xml, String name) {
        Matcher m = Pattern.compile("\\s" + name + "=\"([^\"]+)\"").matcher(xml);
        return m.find() ? m.group(1) : "";
    }

    private static String baseName(Path f) {
        String n = f.getFileName().toString();
        if (n.endsWith(".9.png")) {
            return n.substring(0, n.length() - 6);
        }
        int dot = n.indexOf('.');
        return dot < 0 ? n : n.substring(0, dot);
    }

    /** Đọc file; file .java thì thay comment bằng khoảng trắng (giữ xuống dòng để số dòng vẫn đúng) */
    private static String read(Path f) throws IOException {
        String s = new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
        if (f.toString().endsWith(".java")) {
            Matcher m = COMMENT.matcher(s);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group().replaceAll("[^\\n]", " ")));
            }
            m.appendTail(sb);
            s = sb.toString();
        } else if (f.toString().endsWith(".xml")) {
            Matcher m = XML_COMMENT.matcher(s);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group().replaceAll("[^\\n]", " ")));
            }
            m.appendTail(sb);
            s = sb.toString();
        }
        return s;
    }

    private static List<Path> list(Path dir, String ext) throws IOException {
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(ext)).sorted().collect(Collectors.toList());
        }
    }

    private static List<Path> listDirs(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(Files::isDirectory).collect(Collectors.toList());
        }
    }
}
