import javax.tools.*;
import com.sun.source.util.*;
import com.sun.source.tree.*;
import com.sun.source.doctree.*;
import java.nio.file.*;
import java.nio.charset.*;
import java.util.*;
JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
StandardJavaFileManager files = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);
List<Path> sources;
try (java.util.stream.Stream<Path> stream = Files.walk(Path.of("src"))) { sources = stream.filter(p -> p.toString().endsWith(".java")).toList(); }
JavacTask task = (JavacTask) compiler.getTask(null, files, null, List.of("-proc:none"), null, files.getJavaFileObjectsFromPaths(sources));
DocTrees docs = DocTrees.instance(task);
List<String> missing = new ArrayList<>();
for (CompilationUnitTree unit : task.parse()) {
 new TreePathScanner<Void, Void>() {
  void check(Tree tree) { if (docs.getDocCommentTree(getCurrentPath()) == null) missing.add(unit.getSourceFile().getName() + ": " + tree.getKind() + " " + tree.toString().split("\\R")[0]); }
  @Override public Void visitClass(ClassTree tree, Void unused) { if (!tree.getSimpleName().toString().isEmpty()) check(tree); return super.visitClass(tree, unused); }
  @Override public Void visitMethod(MethodTree tree, Void unused) { check(tree); return super.visitMethod(tree, unused); }
  @Override public Void visitVariable(VariableTree tree, Void unused) {
   Tree parent = getCurrentPath().getParentPath().getLeaf();
   if (parent instanceof ClassTree type) {
    if (type.getKind() == Tree.Kind.RECORD && tree.getModifiers().getFlags().contains(javax.lang.model.element.Modifier.PRIVATE)) {
     DocCommentTree comment = docs.getDocCommentTree(getCurrentPath().getParentPath());
     boolean described = comment != null && comment.getBlockTags().stream().anyMatch(tag -> tag instanceof ParamTree param && param.getName().toString().equals(tree.getName().toString()));
     if (!described) missing.add("record component without @param: " + tree.getName());
    } else check(tree);
   }
   return super.visitVariable(tree, unused);
  }
 }.scan(unit, null);
}
System.out.println("Java files reviewed: " + sources.size());
System.out.println("Missing Javadoc: " + missing.size());
missing.forEach(System.out::println);
files.close();
/exit
