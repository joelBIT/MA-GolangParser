package joelbits.modules.preprocessing.plugins;

import com.google.auto.service.AutoService;
import joelbits.model.ast.protobuf.ASTProtos.Variable;
import joelbits.model.ast.protobuf.ASTProtos.Method;
import joelbits.model.ast.protobuf.ASTProtos.Namespace;
import joelbits.modules.preprocessing.plugins.golang.GolangParser;
import joelbits.modules.preprocessing.plugins.golang.GolangLexer;
import joelbits.modules.preprocessing.plugins.listeners.ClassListener;
import joelbits.modules.preprocessing.plugins.listeners.ImportListener;
import joelbits.modules.preprocessing.plugins.spi.FileParser;
import joelbits.modules.preprocessing.plugins.utils.NamespaceCreator;
import joelbits.modules.preprocessing.utils.ASTNodeCreator;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@AutoService(FileParser.class)
public final class GoParser implements FileParser {
    private static final Logger log = LoggerFactory.getLogger(GoParser.class);
    private final List<String> imports = new ArrayList<>();
    private final List<Namespace> namespaces = new ArrayList<>();
    private final ASTNodeCreator astNodeCreator = new ASTNodeCreator();
    private GolangParser parser;

    @Override
    public byte[] parse(File file) throws IOException {
        loadFile(file);

        NamespaceCreator namespaceCreator = new NamespaceCreator();
        ParseTreeWalker walker = new ParseTreeWalker();
        String namespace = file.getName().substring(0, file.getName().lastIndexOf("."));
        ClassListener classListener = new ClassListener(namespace);
        ImportListener importListener = new ImportListener(imports);

        try {
            ParseTree tree = parser.sourceFile();
            walker.walk(classListener, tree);
            walker.walk(importListener, tree);
        } catch (Exception e) {
            log.error(e.toString(), e);
        }

        namespaceCreator.createNamespace(namespace, classListener.namespaceDeclarations());
        classListener.clear();

        return astNodeCreator.createAstRoot(imports, namespaceCreator.namespaces()).toByteArray();
    }

    private void loadFile(File file) throws IOException {
        clearData();
        org.antlr.v4.runtime.CharStream input = org.antlr.v4.runtime.CharStreams.fromStream(new FileInputStream(file));
        initParser(initLexer(input));
    }

    private CommonTokenStream initLexer(CharStream input) {
        GolangLexer lexer = new GolangLexer(input);
        lexer.removeErrorListeners();
        return new CommonTokenStream(lexer);
    }

    private void initParser(CommonTokenStream tokens) {
        parser = new GolangParser(tokens);
        parser.removeErrorListeners();
        parser.getInterpreter().setPredictionMode(PredictionMode.SLL);
        parser.reset();
    }

    private void clearData() {
        imports.clear();
        namespaces.clear();
    }

    @Override
    public boolean hasBenchmarks(File file) throws IOException {
        loadFile(file);

        String namespace = file.getName().substring(0, file.getName().lastIndexOf("."));
        ClassListener classListener = new ClassListener(namespace);
        ParseTreeWalker walker = new ParseTreeWalker();

        try {
            ParseTree tree = parser.sourceFile();
            walker.walk(classListener, tree);
        } catch (Exception e) {
            log.error(e.toString(), e);
        }

        boolean hasBenchmark = hasBenchmarks(classListener.methods());
        classListener.clear();
        return hasBenchmark;
    }

    private boolean hasBenchmarks(List<Method> methods) {
        for (Method method : methods) {
            for(Variable argument : method.getArgumentsList()) {
                if(argument.getType().getName().contains("*testing.B")) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "go";
    }
}
