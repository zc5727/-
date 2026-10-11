package com.hbjt.fitness.agent.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 知识库加载器：把 knowledge 目录下各分类子目录里的 Markdown 读成内存文档列表。
 *
 * <p>只解析一个刻意限定的 front matter 子集（{@code key: value} 与 {@code key: [a, b]}），
 * 不引入 YAML 依赖——语料格式由自带校验脚本保证，格式一旦跑偏会被校验器拦下，
 * 不需要在运行时容忍各种 YAML 方言。
 *
 * <p>加载失败（目录不存在、文件读不了）不抛出异常：知识库是增强能力，
 * 缺了它应当降级而不是让整个智能体起不来。
 */
@Component
public class KnowledgeLoader {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeLoader.class);

    private static final String INDEX_FILE = "INDEX.MD";

    private final KnowledgeProperties props;

    public KnowledgeLoader(KnowledgeProperties props) {
        this.props = props;
    }

    /**
     * 扫描并解析知识库。
     *
     * @return 文档列表；目录不存在或未启用时返回空列表
     */
    public List<KnowledgeDocument> load() {
        if (!props.isEnabled()) {
            return List.of();
        }
        Path root = Path.of(props.getDir());
        if (!Files.isDirectory(root)) {
            log.warn("知识库目录不存在，检索能力降级：{}", root.toAbsolutePath());
            return List.of();
        }

        List<KnowledgeDocument> docs = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> files = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".md"))
                    .filter(p -> !INDEX_FILE.equalsIgnoreCase(p.getFileName().toString()))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();

            for (Path file : files) {
                try {
                    KnowledgeDocument doc = parse(file, root);
                    if (doc != null) {
                        docs.add(doc);
                    }
                } catch (Exception e) {
                    log.warn("跳过无法解析的知识库文档 {}：{}", file, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("扫描知识库目录失败：{}", e.getMessage());
            return List.of();
        }
        return docs;
    }

    private KnowledgeDocument parse(Path file, Path root) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        String body = text;
        if (text.startsWith("---")) {
            int end = text.indexOf("\n---", 3);
            if (end > 0) {
                meta = parseFrontMatter(text.substring(3, end));
                int bodyStart = text.indexOf('\n', end + 1);
                body = bodyStart > 0 ? text.substring(bodyStart + 1) : "";
            }
        }
        String id = str(meta.get("id"));
        if (id.isBlank()) {
            log.warn("知识库文档缺少 id，已跳过：{}", file);
            return null;
        }
        body = body.strip();
        return new KnowledgeDocument(
                id,
                str(meta.get("title")),
                str(meta.get("category")),
                str(meta.get("level")),
                list(meta.get("equipment")),
                list(meta.get("primary_muscles")),
                str(meta.get("risk_level")),
                list(meta.get("tags")),
                list(meta.get("questions")),
                sections(body),
                body,
                root.relativize(file).toString().replace('\\', '/'),
                body.length());
    }

    private Map<String, Object> parseFrontMatter(String raw) {
        Map<String, Object> meta = new LinkedHashMap<>();
        for (String line : raw.split("\n")) {
            String l = line.strip();
            if (l.isEmpty() || l.startsWith("#")) {
                continue;
            }
            int colon = l.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = l.substring(0, colon).strip();
            String value = l.substring(colon + 1).strip();
            if (value.startsWith("[") && value.endsWith("]")) {
                List<String> items = new ArrayList<>();
                for (String v : value.substring(1, value.length() - 1).split(",")) {
                    String s = v.strip().replaceAll("^[\"']|[\"']$", "");
                    if (!s.isEmpty()) {
                        items.add(s);
                    }
                }
                meta.put(key, items);
            } else {
                meta.put(key, value.replaceAll("^[\"']|[\"']$", ""));
            }
        }
        return meta;
    }

    /** 按 {@code ## 标题} 切章节 */
    private Map<String, String> sections(String body) {
        Map<String, String> out = new LinkedHashMap<>();
        String current = null;
        StringBuilder buf = new StringBuilder();
        for (String line : body.split("\n")) {
            if (line.startsWith("## ")) {
                if (current != null) {
                    out.put(current, buf.toString().strip());
                }
                // 去掉可能的序号前缀，如 "## 1. 要点"
                current = line.substring(3).strip().replaceAll("^\\d+[.、]\\s*", "");
                buf.setLength(0);
            } else if (current != null && !line.startsWith("# ")) {
                buf.append(line).append('\n');
            }
        }
        if (current != null) {
            out.put(current, buf.toString().strip());
        }
        return out;
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v).strip();
    }

    @SuppressWarnings("unchecked")
    private static List<String> list(Object v) {
        if (v instanceof List<?> l) {
            return (List<String>) l;
        }
        if (v instanceof String s && !s.isBlank()) {
            return List.of(s);
        }
        return List.of();
    }
}
