////////////////////////////////////////////////////////////////////////////////
// checkstyle: Checks Java source code for adherence to a set of rules.
// Copyright (C) 2001-2011  Oliver Burn
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
////////////////////////////////////////////////////////////////////////////////
package com.github.sevntu.checkstyle.checks.design;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import antlr.collections.ASTEnumeration;

import com.puppycrawl.tools.checkstyle.api.Check;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.FullIdent;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;

/**
 * <p>
 * Checks distance between declaration of variable and its first usage.
 * </p>
 * Example #1:
 * <pre>
 *      <code>int count;
 *      a = a + b;
 *      b = a + a;
 *      count = b; // DECLARATION OF VARIABLE 'count'
 *                 // SHOULD BE HERE (distance = 3)</code>
 * </pre>
 * Example #2:
 * <pre>
 *     <code>int count;
 *     {
 *         a = a + b;
 *         count = b; // DECLARATION OF VARIABLE 'count'
 *                    // SHOULD BE HERE (distance = 2)
 *     }</code>
 * </pre>
 * 
 * <p>
 * Check can detect block of initialization methods. If variable is used in
 * such block and after variable declaration there is no other statements, then
 * distance=1. Example:
 * </p>
 * <p>
 * <b>Case #1:</b>
 * <pre>
 * int <b>minutes</b> = 5;
 * Calendar cal = Calendar.getInstance();
 * cal.setTimeInMillis(timeNow);
 * cal.set(Calendar.SECOND, 0);
 * cal.set(Calendar.MILLISECOND, 0);
 * cal.set(Calendar.HOUR_OF_DAY, hh);
 * cal.set(Calendar.MINUTE, <b>minutes</b>);
 * 
 * Distance for variable <b>minutes</b> is 1, although this variable is used in fifth method call.
 * </pre>
 * </p>
 * <p>
 * <b>Case #2:</b>
 * <pre>
 * int <b>minutes</b> = 5;
 * Calendar cal = Calendar.getInstance();
 * cal.setTimeInMillis(timeNow);
 * cal.set(Calendar.SECOND, 0);
 * cal.set(Calendar.MILLISECOND, 0);
 * <i>System.out.println(cal);</i>
 * cal.set(Calendar.HOUR_OF_DAY, hh);
 * cal.set(Calendar.MINUTE, <b>minutes</b>);
 * 
 * Distance for variable <b>minutes</b> is 6, because between declaration and usage there is one
 * more expression except initialization block.
 * </pre>
 * </p>
 * 
 * There are several additional options to configure check:
 * <pre>
 * 1. allowedDistance - allows to set distance between declaration
 * of variable and its first usage.
 * 2. ignoreVariablePattern - allows to set RegEx pattern for ignoring
 * distance calculation for variables listed in this pattern.
 * 3. validateBetweenScopes - allows to calculate distance between declaration
 * of variable and its first usage in different scopes.
 * 4. ignoreFinal - allows to ignore variables with 'final' modifier.
 * </pre>
 * ATTENTION!! (Not supported cases)
 * <pre>
 * Case #1:
 * <code>{
 * int c;
 * int a = 3;
 * int b = 2;
 *     {
 *     a = a + b;
 *     c = b;
 *     }
 * }</code>
 *
 * Distance for variable 'a' = 1;
 * Distance for variable 'b' = 1;
 * Distance for variable 'c' = 2.
 * </pre>
 * As distance by default is 1 the Check doesn't raise warning for variables 'a'
 * and 'b' to move them into the block.
 * <pre>
 * Case #2:
 * <code>int sum = 0;
 * for (int i = 0; i < 20; i++) {
 *     a++;
 *     b--;
 *     sum++;
 *     if (sum > 10) {
 *         res = true;
 *     }
 * }</code>
 * Distance for variable 'sum' = 3.
 * </pre>
 * <p>
 * As distance more then default one, the Check raises warning for variable
 * 'sum' to move it into the 'for(...)' block. But there is situation when
 * variable 'sum' hasn't to be 0 within each iteration. So, to avoid such
 * warnings you can use Suppression Filter, provided by Checkstyle, for the
 * whole class.
 * </p>
 * @author <a href="mailto:rd.ryly@gmail.com">Ruslan Diachenko</a>
 * @author <a href="mailto:barataliba@gmail.com">Baratali Izmailov</a>
 */
public class VariableDeclarationUsageDistanceCheck extends Check
{
	/**
	 * Warning message key.
	 */
	public final static String MSG_KEY = "variable.declaration.usage.distance";
	
    /**
     * Default value of distance between declaration of variable and its first
     * usage.
     */
    private static final int DEFAULT_DISTANCE = 3;

    /** Allowed distance between declaration of variable and its first usage. */
    private int mAllowedDistance = DEFAULT_DISTANCE;

    /**
     * RegExp pattern to ignore distance calculation for variables listed in
     * this pattern.
     */
    private Pattern mIgnoreVariablePattern = Pattern.compile("");

    /**
     * Allows to calculate distance between declaration of variable and its
     * first usage in different scopes.
     */
    private boolean mValidateBetweenScopes;

    /** Allows to ignore variables with 'final' modifier. */
    private boolean mIgnoreFinal = true;

    /**
     * Sets an allowed distance between declaration of variable and its first
     * usage.
     * @param aAllowedDistance
     *        Allowed distance between declaration of variable and its first
     *        usage.
     */
    public void setAllowedDistance(int aAllowedDistance)
    {
        this.mAllowedDistance = aAllowedDistance;
    }

    /**
     * Sets RegExp pattern to ignore distance calculation for variables listed
     * in this pattern.
     * @param aIgnorePattern
     *        Pattern contains ignored variables.
     */
    public void setIgnoreVariablePattern(String aIgnorePattern)
    {
        this.mIgnoreVariablePattern = Pattern.compile(aIgnorePattern);
    }

    /**
     * Sets option which allows to calculate distance between declaration of
     * variable and its first usage in different scopes.
     * @param aValidateBetweenScopes
     *        Defines if allow to calculate distance between declaration of
     *        variable and its first usage in different scopes or not.
     */
    public void setValidateBetweenScopes(boolean aValidateBetweenScopes)
    {
        this.mValidateBetweenScopes = aValidateBetweenScopes;
    }

    /**
     * Sets ignore option for variables with 'final' modifier.
     * @param aIgnoreFinal
     *        Defines if ignore variables with 'final' modifier or not.
     */
    public void setIgnoreFinal(boolean aIgnoreFinal)
    {
        this.mIgnoreFinal = aIgnoreFinal;
    }

    @Override
    public int[] getDefaultTokens()
    {
        return new int[] {TokenTypes.VARIABLE_DEF};
    }

    @Override
    public void visitToken(DetailAST aAST)
    {
        final int parentType = aAST.getParent().getType();
        final DetailAST modifiers = aAST.getFirstChild();

        if ((mIgnoreFinal && modifiers.branchContains(TokenTypes.FINAL))
                || parentType == TokenTypes.OBJBLOCK)
        {
            ;// no code
        }
        else {
            final DetailAST variable = aAST.findFirstToken(TokenTypes.IDENT);

            if (!isVariableMatchesIgnorePattern(variable.getText())) {
                final DetailAST semicolonAst = aAST.getNextSibling();
                Entry<DetailAST, Integer> entry = null;
                if (mValidateBetweenScopes) {
                    entry = calculateDistanceBetweenScopes(semicolonAst, variable);
                }
                else {
                    entry = calculateDistanceInSingleScope(semicolonAst, variable);
                }
                final DetailAST variableUsageAst = entry.getKey();
                int dist = entry.getValue();
                if (dist > mAllowedDistance
                        && !isInitializationSequence(variableUsageAst, variable.getText()))
                {
                    log(variable.getLineNo(),
                            MSG_KEY, variable.getText(), dist, mAllowedDistance);
                }
            }
        }
    }

    /**
     * Get name of instance whose method is called.
     * @param aMethodCallAst
     *        DetailAST of METHOD_CALL.
     * @return name of instance.
     */
    private static String getInstanceName(DetailAST aMethodCallAst)
    {
        final String methodCallName =
                FullIdent.createFullIdentBelow(aMethodCallAst).getText();
        final int lastDotIndex = methodCallName.lastIndexOf('.');
        String instanceName = "";
        if (lastDotIndex != -1) {
            instanceName = methodCallName.substring(0, lastDotIndex);
        }
        return instanceName;
    }

    /**
     * Processes statements until usage of variable to detect sequence of
     * initialization methods.
     * @param aVariableUsageAst
     *        DetailAST of expression that uses variable named aVariableName.
     * @param aVariableName
     *        name of considered variable.
     * @return true if statements between declaration and usage of variable are
     *         initialization methods.
     */
    private static boolean isInitializationSequence(
            DetailAST aVariableUsageAst, String aVariableName)
    {
        boolean result = true;
        boolean isUsedVariableDeclarationFound = false;
        DetailAST currentSiblingAst = aVariableUsageAst;
        String initInstanceName = "";

        while (result
                && !isUsedVariableDeclarationFound
                && currentSiblingAst != null)
        {

            switch (currentSiblingAst.getType()) {

            case TokenTypes.EXPR:
                final DetailAST methodCallAst = currentSiblingAst.getFirstChild();

                if (methodCallAst != null
                        && methodCallAst.getType() == TokenTypes.METHOD_CALL)
                {
                    final String instanceName =
                            getInstanceName(methodCallAst);
                    // method is called without instance
                    if (instanceName.isEmpty()) {
                        result = false;
                    }
                    // differs from previous instance
                    else if (!instanceName.equals(initInstanceName)) {
                        if (!initInstanceName.isEmpty()) {
                            result = false;
                        }
                        else {
                            initInstanceName = instanceName;
                        }
                    }
                }
                else { // is not method call
                    result = false;
                }
                break;

            case TokenTypes.VARIABLE_DEF:
                final String currentVariableName = currentSiblingAst.
                        findFirstToken(TokenTypes.IDENT).getText();
                isUsedVariableDeclarationFound = aVariableName.equals(currentVariableName);
                break;

            case TokenTypes.SEMI:
                break;

            default:
                result = false;
            }
            
            currentSiblingAst = currentSiblingAst.getPreviousSibling();
        }

        return result;
    }

    /**
     * Calculates distance between declaration of variable and its first usage
     * in single scope.
     * @param aSemicolonAst
     *        Regular node of Ast which is checked for content of checking
     *        variable.
     * @param aVariableIdentAst
     *        Variable which distance is calculated for.
     * @return entry which contains expression with variable usage and distance.
     */
    private Entry<DetailAST, Integer> calculateDistanceInSingleScope(
            DetailAST aSemicolonAst, DetailAST aVariableIdentAst)
    {
        int dist = 0;
        boolean firstUsageFound = false;
        DetailAST currentAst = aSemicolonAst;
        DetailAST variableUsageAst = null;

        while (!firstUsageFound && currentAst != null
                && currentAst.getType() != TokenTypes.RCURLY)
        {
            if (currentAst.getFirstChild() != null) {

                if (isChild(currentAst, aVariableIdentAst)) {

                    switch (currentAst.getType()) {
                    case TokenTypes.VARIABLE_DEF:
                        dist++;
                        break;
                    case TokenTypes.SLIST:
                        dist = 0;
                        break;
                    case TokenTypes.LITERAL_FOR:
                    case TokenTypes.LITERAL_WHILE:
                    case TokenTypes.LITERAL_DO:
                    case TokenTypes.LITERAL_IF:
                    case TokenTypes.LITERAL_SWITCH:
                        if (isVariableInOperatorExpr(currentAst, aVariableIdentAst)) {
                            dist++;
                        }
                        else { // variable usage is in inner scope
                            // reset counters, because we can't determine distance
                            dist = 0;
                        }
                        break;
                    default:
                        if (currentAst.branchContains(TokenTypes.SLIST)) {
                            dist = 0;
                        }
                        else {
                            dist++;
                        }
                    }
                    variableUsageAst = currentAst;
                    firstUsageFound = true;
                }
                else if (currentAst.getType() != TokenTypes.VARIABLE_DEF) {
                    dist++;
                }
            }
            currentAst = currentAst.getNextSibling();
        }

        // If variable wasn't used after its declaration, distance is 0.
        if (!firstUsageFound) {
            dist = 0;
        }

        return new SimpleEntry<DetailAST, Integer>(variableUsageAst, dist);
    }

    /**
     * Calculates distance between declaration of variable and its first usage
     * in multiple scopes.
     * @param aAST
     *        Regular node of Ast which is checked for content of checking
     *        variable.
     * @param aVariable
     *        Variable which distance is calculated for.
     * @return entry which contains expression with variable usage and distance.
     */
    private Entry<DetailAST, Integer> calculateDistanceBetweenScopes(
            DetailAST aAST, DetailAST aVariable)
    {
        int dist = 0;
        DetailAST currentScopeAst = aAST;
        DetailAST variableUsageAst = null;
        while (currentScopeAst != null) {
            final List<DetailAST> variableUsageExpressions = new ArrayList<DetailAST>();
            DetailAST currentStatementAst = currentScopeAst;
            currentScopeAst = null;
            while (currentStatementAst != null
                    && currentStatementAst.getType() != TokenTypes.RCURLY)
            {
                if (currentStatementAst.getFirstChild() != null) {
                    if (isChild(currentStatementAst, aVariable)) {
                        variableUsageExpressions.add(currentStatementAst);
                    }
                    // If expression doesn't contain variable and this variable
                    // hasn't been met yet, than distance + 1.
                    else if (variableUsageExpressions.size() == 0
                            && currentStatementAst.getType() != TokenTypes.VARIABLE_DEF)
                    {
                        dist++;
                    }
                }
                currentStatementAst = currentStatementAst.getNextSibling();
            }
            // If variable usage exists in a single scope, then look into
            // this scope and count distance until variable usage.
            if (variableUsageExpressions.size() == 1) {
                final DetailAST blockWithVariableUsage = variableUsageExpressions
                        .get(0);
                DetailAST exprWithVariableUsage = null;
                switch (blockWithVariableUsage.getType()) {
                case TokenTypes.VARIABLE_DEF:
                case TokenTypes.EXPR:
                    dist++;
                    break;
                case TokenTypes.LITERAL_FOR:
                case TokenTypes.LITERAL_WHILE:
                case TokenTypes.LITERAL_DO:
                    exprWithVariableUsage = getFirstNodeInsideForWhileDoWhileBlocks(
                            blockWithVariableUsage, aVariable);
                    break;
                case TokenTypes.LITERAL_IF:
                    exprWithVariableUsage = getFirstNodeInsideIfBlock(
                            blockWithVariableUsage, aVariable);
                    break;
                case TokenTypes.LITERAL_SWITCH:
                    exprWithVariableUsage = getFirstNodeInsideSwitchBlock(
                            blockWithVariableUsage, aVariable);
                    break;
                case TokenTypes.LITERAL_TRY:
                    exprWithVariableUsage =
                        getFirstNodeInsideTryCatchFinallyBlocks(blockWithVariableUsage, aVariable);
                    break;
                default:
                    exprWithVariableUsage = blockWithVariableUsage.getFirstChild();
                }
                currentScopeAst = exprWithVariableUsage;
                if (exprWithVariableUsage != null) {
                    variableUsageAst = exprWithVariableUsage;
                }
                else {
                    variableUsageAst = blockWithVariableUsage;
                }
            }
            // If variable usage exists in different scopes, then distance =
            // distance until variable first usage.
            else if (variableUsageExpressions.size() > 1) {
                dist++;
                variableUsageAst = variableUsageExpressions.get(0);
            }
            // If there's no any variable usage, then distance = 0.
            else {
                variableUsageAst = null;
            }
        }
        return new SimpleEntry<DetailAST, Integer>(variableUsageAst, dist);
    }

    /**
     * Gets first Ast node inside FOR, WHILE or DO-WHILE blocks if variable
     * usage is met only inside the block (not in its declaration!).
     * @param aBlock
     *        Ast node represents FOR, WHILE or DO-WHILE block.
     * @param aVariable
     *        Variable which is checked for content in block.
     * @return If variable usage is met only inside the block
     *         (not in its declaration!) than return the first Ast node
     *         of this block, otherwise - null.
     */
    private DetailAST getFirstNodeInsideForWhileDoWhileBlocks(
            DetailAST aBlock, DetailAST aVariable)
    {
        DetailAST firstNodeInsideBlock = null;

        if (!isVariableInOperatorExpr(aBlock, aVariable)) {
            DetailAST currentNode = null;

            // Find currentNode for DO-WHILE block.
            if (aBlock.getType() == TokenTypes.LITERAL_DO) {
                currentNode = aBlock.getFirstChild();
            }
            // Find currentNode for FOR or WHILE block.
            else {
                // Looking for RPAREN ( ')' ) token to mark the end of operator
                // expression.
                currentNode = aBlock.findFirstToken(TokenTypes.RPAREN);
                if (currentNode != null) {
                    currentNode = currentNode.getNextSibling();
                }
            }

            if (currentNode != null) {
                final int currentNodeType = currentNode.getType();

                if (currentNodeType == TokenTypes.SLIST) {
                    firstNodeInsideBlock = currentNode.getFirstChild();
                }
                else if (currentNodeType == TokenTypes.VARIABLE_DEF
                        || currentNodeType == TokenTypes.EXPR)
                {
                    ; // no code
                }
                else {
                    firstNodeInsideBlock = currentNode;
                }
            }
        }

        return firstNodeInsideBlock;
    }

    /**
     * Gets first Ast node inside IF block if variable usage is met
     * only inside the block (not in its declaration!).
     * @param aBlock
     *        Ast node represents IF block.
     * @param aVariable
     *        Variable which is checked for content in block.
     * @return If variable usage is met only inside the block
     *         (not in its declaration!) than return the first Ast node
     *         of this block, otherwise - null.
     */
    private DetailAST getFirstNodeInsideIfBlock(
            DetailAST aBlock, DetailAST aVariable)
    {
        DetailAST firstNodeInsideBlock = null;

        if (!isVariableInOperatorExpr(aBlock, aVariable)) {
            DetailAST currentNode = aBlock.getLastChild();
            final List<DetailAST> variableUsageExpressions =
                    new ArrayList<DetailAST>();

            while (currentNode != null
                    && currentNode.getType() == TokenTypes.LITERAL_ELSE)
            {
                final DetailAST previousNode =
                        currentNode.getPreviousSibling();

                // Checking variable usage inside IF block.
                if (isChild(previousNode, aVariable)) {
                    variableUsageExpressions.add(previousNode);
                }

                // Looking into ELSE block, get its first child and analyze it.
                currentNode = currentNode.getFirstChild();

                if (currentNode.getType() == TokenTypes.LITERAL_IF) {
                    currentNode = currentNode.getLastChild();
                }
                else if (isChild(currentNode, aVariable)) {
                    variableUsageExpressions.add(currentNode);
                    currentNode = null;
                }
            }

            // If IF block doesn't include ELSE than analyze variable usage
            // only inside IF block.
            if (currentNode != null
                    && isChild(currentNode, aVariable))
            {
                variableUsageExpressions.add(currentNode);
            }

            // If variable usage exists in several related blocks, then
            // firstNodeInsideBlock = null, otherwise if variable usage exists
            // only inside one block, then get node from
            // variableUsageExpressions.
            if (variableUsageExpressions.size() == 1) {
                firstNodeInsideBlock = variableUsageExpressions.get(0);
            }
        }

        return firstNodeInsideBlock;
    }

    /**
     * Gets first Ast node inside SWITCH block if variable usage is met
     * only inside the block (not in its declaration!).
     * @param aBlock
     *        Ast node represents SWITCH block.
     * @param aVariable
     *        Variable which is checked for content in block.
     * @return If variable usage is met only inside the block
     *         (not in its declaration!) than return the first Ast node
     *         of this block, otherwise - null.
     */
    private DetailAST getFirstNodeInsideSwitchBlock(
            DetailAST aBlock, DetailAST aVariable)
    {
        DetailAST firstNodeInsideBlock = null;

        if (!isVariableInOperatorExpr(aBlock, aVariable)) {
            DetailAST currentNode = aBlock
                    .findFirstToken(TokenTypes.CASE_GROUP);
            final List<DetailAST> variableUsageExpressions =
                    new ArrayList<DetailAST>();

            // Checking variable usage inside all CASE blocks.
            while (currentNode != null
                    && currentNode.getType() == TokenTypes.CASE_GROUP)
            {
                final DetailAST lastNodeInCaseGroup =
                        currentNode.getLastChild();

                if (isChild(lastNodeInCaseGroup, aVariable)) {
                    variableUsageExpressions.add(lastNodeInCaseGroup);
                }
                currentNode = currentNode.getNextSibling();
            }

            // If variable usage exists in several related blocks, then
            // firstNodeInsideBlock = null, otherwise if variable usage exists
            // only inside one block, then get node from
            // variableUsageExpressions.
            if (variableUsageExpressions.size() == 1) {
                firstNodeInsideBlock = variableUsageExpressions.get(0);
            }
        }

        return firstNodeInsideBlock;
    }

    /**
     * Gets first Ast node inside TRY-CATCH-FINALLY blocks if variable usage is
     * met only inside the block (not in its declaration!).
     * @param aBlock
     *        Ast node represents TRY-CATCH-FINALLY block.
     * @param aVariable
     *        Variable which is checked for content in block.
     * @return If variable usage is met only inside the block
     *         (not in its declaration!) than return the first Ast node
     *         of this block, otherwise - null.
     */
    private static DetailAST getFirstNodeInsideTryCatchFinallyBlocks(
            DetailAST aBlock, DetailAST aVariable)
    {
        DetailAST currentNode = aBlock.getFirstChild();
        final List<DetailAST> variableUsageExpressions =
                new ArrayList<DetailAST>();

        // Checking variable usage inside TRY block.
        if (isChild(currentNode, aVariable)) {
            variableUsageExpressions.add(currentNode);
        }

        // Switch on CATCH block.
        currentNode = currentNode.getNextSibling();

        // Checking variable usage inside all CATCH blocks.
        while (currentNode != null
                && currentNode.getType() == TokenTypes.LITERAL_CATCH)
        {
            final DetailAST catchBlock = currentNode.getLastChild();

            if (isChild(catchBlock, aVariable)) {
                variableUsageExpressions.add(catchBlock);
            }
            currentNode = currentNode.getNextSibling();
        }

        // Checking variable usage inside FINALLY block.
        if (currentNode != null) {
            final DetailAST finalBlock = currentNode.getLastChild();

            if (isChild(finalBlock, aVariable)) {
                variableUsageExpressions.add(finalBlock);
            }
        }

        DetailAST variableUsageNode = null;

        // If variable usage exists in several related blocks, then
        // firstNodeInsideBlock = null, otherwise if variable usage exists
        // only inside one block, then get node from
        // variableUsageExpressions.
        if (variableUsageExpressions.size() == 1) {
            variableUsageNode = variableUsageExpressions.get(0).getFirstChild();
        }

        return variableUsageNode;
    }

    /**
     * Checks if variable is in operator declaration. For instance:
     * <pre>
     * boolean b = true;
     * if (b) {...}
     * </pre>
     * Variable 'b' is in declaration of operator IF.
     * @param aOperator
     *        Ast node which represents operator.
     * @param aVariable
     *        Variable which is checked for content in operator.
     * @return true if operator contains variable in its declaration, otherwise
     *         - false.
     */
    private boolean isVariableInOperatorExpr(
            DetailAST aOperator, DetailAST aVariable)
    {
        boolean isVarInOperatorDeclr = false;
        final DetailAST openingBracket =
                aOperator.findFirstToken(TokenTypes.LPAREN);

        if (openingBracket != null) {
            // Get EXPR between brackets
            DetailAST exprBetweenBrackets = openingBracket
                    .getNextSibling();

            // Look if variable is in operator expression
            while (exprBetweenBrackets.getType() != TokenTypes.RPAREN) {

                if (isChild(exprBetweenBrackets, aVariable)) {
                    isVarInOperatorDeclr = true;
                    break;
                }
                exprBetweenBrackets = exprBetweenBrackets.getNextSibling();
            }

            // Variable may be met in ELSE declaration or in CASE declaration.
            // So, check variable usage in these declarations.
            if (!isVarInOperatorDeclr) {
                switch (aOperator.getType()) {
                case TokenTypes.LITERAL_IF:
                    final DetailAST elseBlock = aOperator.getLastChild();

                    if (elseBlock.getType() == TokenTypes.LITERAL_ELSE) {
                        // Get IF followed by ELSE
                        final DetailAST firstNodeInsideElseBlock = elseBlock
                                .getFirstChild();

                        if (firstNodeInsideElseBlock.getType()
                                == TokenTypes.LITERAL_IF)
                        {
                            isVarInOperatorDeclr |=
                                    isVariableInOperatorExpr(
                                            firstNodeInsideElseBlock,
                                            aVariable);
                        }
                    }
                    break;

                case TokenTypes.LITERAL_SWITCH:
                    DetailAST currentCaseBlock = aOperator
                            .findFirstToken(TokenTypes.CASE_GROUP);

                    while (currentCaseBlock != null
                            && currentCaseBlock.getType()
                            == TokenTypes.CASE_GROUP)
                    {
                        final DetailAST firstNodeInsideCaseBlock =
                                currentCaseBlock.getFirstChild();

                        if (isChild(firstNodeInsideCaseBlock,
                                aVariable))
                        {
                            isVarInOperatorDeclr = true;
                            break;
                        }
                        currentCaseBlock = currentCaseBlock.getNextSibling();
                    }
                    break;

                default:
                    ;// no code
                }
            }
        }

        return isVarInOperatorDeclr;
    }

    /**
     * Checks if Ast node contains given element.
     * @param aParent
     *        Node of AST.
     * @param aAST
     *        Ast element which is checked for content in Ast node.
     * @return true if Ast element was found in Ast node, otherwise - false.
     */
    private static boolean isChild(DetailAST aParent, DetailAST aAST)
    {
        boolean isChild = false;
        final ASTEnumeration astList = aParent.findAllPartial(aAST);

        while (astList.hasMoreNodes()) {
            final DetailAST ast = (DetailAST) astList.nextNode();
            DetailAST astParent = ast.getParent();

            while (astParent != null) {

                if (astParent.equals(aParent)
                        && astParent.getLineNo() == aParent.getLineNo())
                {
                    isChild = true;
                    break;
                }
                astParent = astParent.getParent();
            }
        }

        return isChild;
    }

    /**
     * Checks if entrance variable is contained in ignored pattern.
     * @param aVariable
     *        Variable which is checked for content in ignored pattern.
     * @return true if variable was found, otherwise - false.
     */
    private boolean isVariableMatchesIgnorePattern(String aVariable)
    {
        final Matcher matcher = mIgnoreVariablePattern.matcher(aVariable);
        return matcher.matches();
    }
}
