package joelbits.modules.preprocessing.plugins.listeners;

import joelbits.model.ast.protobuf.ASTProtos.Expression;
import joelbits.model.ast.protobuf.ASTProtos.Expression.ExpressionType;
import joelbits.model.ast.protobuf.ASTProtos.Statement.StatementType;
import joelbits.model.ast.protobuf.ASTProtos.Statement;
import joelbits.modules.preprocessing.plugins.golang.GolangBaseListener;
import joelbits.modules.preprocessing.plugins.golang.GolangParser.StatementContext;
import joelbits.modules.preprocessing.plugins.golang.GolangParser.SimpleStmtContext;
import joelbits.modules.preprocessing.plugins.golang.GolangParser.ExpressionContext;
import joelbits.modules.preprocessing.plugins.golang.GolangParser.ForStmtContext;
import joelbits.modules.preprocessing.plugins.golang.GolangParser.DeclarationContext;
import joelbits.modules.preprocessing.plugins.golang.GolangParser.IfStmtContext;
import joelbits.modules.preprocessing.plugins.golang.GolangParser.BlockContext;
import joelbits.modules.preprocessing.plugins.utils.ASTNodeCreator;

import java.util.ArrayList;
import java.util.List;

public final class StatementListener extends GolangBaseListener {
    private final ASTNodeCreator astNodeCreator = new ASTNodeCreator();
    private final List<Statement> statements = new ArrayList<>();
    private final List<Expression> expressions = new ArrayList<>();

    @Override
    public void enterIfStmt(IfStmtContext ctx) {
        String condition = "";
        if (ctx.expression() != null) {
            condition = ctx.expression().getText();
        }

        List<Statement> ifBodyStatements = new ArrayList<>();
        List<Expression> ifBodyExpressions = new ArrayList<>();
        for (BlockContext block : ctx.block()) {
            StatementListener statementListener = new StatementListener();
            statementListener.enterBlock(block);
            ifBodyStatements.addAll(statementListener.statements());
            ifBodyExpressions.addAll(statementListener.expressions());
        }

        Expression conditionExpression = astNodeCreator
                .createMethodBodyExpression(ExpressionType.CONDITIONAL, condition, "");
        statements.add(astNodeCreator
                .createStatement(StatementType.IF, conditionExpression, ifBodyStatements, ifBodyExpressions));
    }

    @Override
    public void enterExpression(ExpressionContext ctx) {
        ExpressionListener expressionListener = new ExpressionListener();
        expressionListener.enterExpression(ctx);
        expressions.addAll(expressionListener.expressions());
    }

    @Override
    public void enterForStmt(ForStmtContext ctx) {
        String condition = "";
        if (ctx.rangeClause() != null) {
            condition = ctx.rangeClause().getText();
        }

        List<Statement> forBodyStatements = new ArrayList<>();
        List<Expression> forBodyExpressions = new ArrayList<>();
        if (ctx.block() != null) {
            StatementListener statementListener = new StatementListener();
            statementListener.enterBlock(ctx.block());
            forBodyStatements.addAll(statementListener.statements());
            forBodyExpressions.addAll(statementListener.expressions());
        }

        Expression conditionExpression = astNodeCreator
                .createMethodBodyExpression(ExpressionType.CONDITIONAL, condition, "");
        statements.add(astNodeCreator
                .createStatement(StatementType.FOR, conditionExpression, forBodyStatements, forBodyExpressions));
    }

    @Override
    public void enterDeclaration(DeclarationContext ctx) {
        ExpressionListener expressionListener = new ExpressionListener();
        if (ctx.varDecl() != null) {
            expressionListener.enterVarDecl(ctx.varDecl());
            expressions.addAll(expressionListener.expressions());
        } else if (ctx.typeDecl() != null) {
            expressionListener.enterTypeDecl(ctx.typeDecl());
            expressions.addAll(expressionListener.expressions());
        }
    }

    @Override
    public void enterBlock(BlockContext ctx) {
        if (ctx.statementList() != null && ctx.statementList().statement() != null) {
            for (StatementContext statement : ctx.statementList().statement()) {
                StatementListener statementListener = new StatementListener();
                statementListener.enterStatement(statement);
                statements.add(astNodeCreator
                        .createBlockStatement(statementListener.expressions(), statementListener.statements()));
            }
        }
    }

    @Override
    public void enterStatement(StatementContext ctx) {
        StatementListener statementListener = new StatementListener();
        if (ctx.forStmt() != null) {
            statementListener.enterForStmt(ctx.forStmt());
            statements.addAll(statementListener.statements());
        } else if (ctx.ifStmt() != null) {
            statementListener.enterIfStmt(ctx.ifStmt());
            statements.addAll(statementListener.statements());
        } else if (ctx.declaration() != null) {
            statementListener.enterDeclaration(ctx.declaration());
            expressions.addAll(statementListener.expressions());
        } else if (ctx.block() != null) {
            statementListener.enterBlock(ctx.block());
            statements.addAll(statementListener.statements());
        } else if (ctx.simpleStmt() != null) {
            statementListener.enterSimpleStmt(ctx.simpleStmt());
            expressions.addAll(statementListener.expressions());
        }
    }

    @Override
    public void enterSimpleStmt(SimpleStmtContext ctx) {
        if (ctx.expressionStmt() != null && ctx.expressionStmt().expression() != null) {
            StatementListener statementListener = new StatementListener();
            statementListener.enterExpression(ctx.expressionStmt().expression());
            expressions.addAll(statementListener.expressions());
        }
    }

    List<Statement> statements() {
        return statements;
    }

    List<Expression> expressions() {
        return expressions;
    }
}
