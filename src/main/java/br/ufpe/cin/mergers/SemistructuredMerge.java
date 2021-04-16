package br.ufpe.cin.mergers;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.util.List;

import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import br.ufpe.cin.app.JFSTMerge;
import br.ufpe.cin.exceptions.ExceptionUtils;
import br.ufpe.cin.exceptions.SemistructuredMergeException;
import br.ufpe.cin.exceptions.TextualMergeException;
import br.ufpe.cin.files.FilesManager;
import br.ufpe.cin.mergers.handlers.ConflictHandler;
import br.ufpe.cin.mergers.handlers.DeletionsHandler;
import br.ufpe.cin.mergers.handlers.DuplicatedDeclarationHandler;
import br.ufpe.cin.mergers.handlers.InitializationBlocksHandler;
import br.ufpe.cin.mergers.handlers.InitializationBlocksHandlerMultipleBlocks;
import br.ufpe.cin.mergers.handlers.LegacyMethodAndConstructorRenamingAndDeletionHandler;
import br.ufpe.cin.mergers.handlers.MethodAndConstructorRenamingAndDeletionHandler;
import br.ufpe.cin.mergers.handlers.NewElementReferencingEditedOneHandler;
import br.ufpe.cin.mergers.handlers.TypeAmbiguityErrorHandler;
import br.ufpe.cin.mergers.util.MergeContext;
import br.ufpe.cin.mergers.util.RenamingUtils;
import br.ufpe.cin.parser.JParser;
import br.ufpe.cin.printers.Prettyprinter;
import cide.gparser.ParseException;
import cide.gparser.TokenMgrError;
import de.ovgu.cide.fstgen.ast.FSTNode;
import de.ovgu.cide.fstgen.ast.FSTNonTerminal;
import de.ovgu.cide.fstgen.ast.FSTTerminal;

/**
 * Represents semistructured merge. Semistrucutred merge is based on the concept
 * of <i>superimposition</i> of ASTs. Superimposition merges trees recursively,
 * beginning from the root, based on structural and nominal similarities.
 * 
 * @author Guilherme
 */
public final class SemistructuredMerge {

	private enum SuperimpositionStep {
		Left_Base, LeftBase_Right
	}

	static final String MERGE_SEPARATOR = "##FSTMerge##";
	static final String SEMANTIC_MERGE_MARKER = "~~FSTMerge~~";

	private static List<ConflictHandler> assembleListOfHandlers() {
		ImmutableList.Builder<ConflictHandler> builder = new ImmutableList.Builder<>();

		if (JFSTMerge.isTypeAmbiguityErrorHandlerEnabled)
			builder.add(new TypeAmbiguityErrorHandler());

		if (JFSTMerge.isNewElementReferencingEditedOneHandlerEnabled)
			builder.add(new NewElementReferencingEditedOneHandler());

		if(JFSTMerge.isMethodAndConstructorRenamingAndDeletionHandlerEnabled)
			builder.add(new MethodAndConstructorRenamingAndDeletionHandler());
    
    if(!JFSTMerge.isInitializationBlocksHandlerEnabled && 
				JFSTMerge.isInitializationBlocksHandlerMultipleBlocksEnabled)
      builder.add(new InitializationBlocksHandlerMultipleBlocks());

		if (JFSTMerge.isInitializationBlocksHandlerEnabled)
			builder.add(new InitializationBlocksHandler());

		if (JFSTMerge.isDuplicatedDeclarationHandlerEnabled)
			builder.add(new DuplicatedDeclarationHandler());

		builder.add(new DeletionsHandler());

		return builder.build();
	}

	/**
	 * Three-way semistructured merge of three given files.
	 * 
	 * @param left
	 * @param base
	 * @param right
	 * @param context an empty MergeContext to store relevant information of the
	 *                merging process.
	 * @return string representing the merge result.
	 * @throws SemistructuredMergeException
	 * @throws TextualMergeException
	 */
	public static String merge(File left, File base, File right, MergeContext context)
			throws SemistructuredMergeException, TextualMergeException {
		return merge(left, base, right, context, assembleListOfHandlers());
	}

	public static String merge(File left, File base, File right, MergeContext context,
			List<ConflictHandler> conflictHandlers) throws SemistructuredMergeException, TextualMergeException {
		try {
			// parsing the files to be merged
			JParser parser = new JParser();
			FSTNode leftTree = parser.parse(left);
			FSTNode baseTree = parser.parse(base);
			FSTNode rightTree = parser.parse(right);

			// merging
			context.join(merge(leftTree, baseTree, rightTree));

			// handling special kinds of conflicts
			context.semistructuredOutput = Prettyprinter.print(context.superImposedTree); // partial result of
																							// semistructured merge is
																							// necessary for further
																							// processing
			for (ConflictHandler conflictHandler : conflictHandlers) {

				try {
					conflictHandler.handle(context);
				} catch (TextualMergeException e) {
					String message = ExceptionUtils.getCauseMessage(e);
					throw new SemistructuredMergeException(message, context);
				}

			}

		} catch (ParseException | FileNotFoundException | UnsupportedEncodingException | TokenMgrError ex) {
			String message = ExceptionUtils.getCauseMessage(ex);
			if (ex instanceof FileNotFoundException) // FileNotFoundException does not support custom messages
				message = "The merged file was deleted in one version.";
			throw new SemistructuredMergeException(message, context);
		}

		// during the parsing process, code indentation is typically lost, so we
		// reindent the code
		return Prettyprinter.print(context.superImposedTree);
	}

	/**
	 * Merges the AST representation of previous given java files.
	 * 
	 * @param left  tree
	 * @param base  tree
	 * @param right tree
	 * @throws TextualMergeException
	 */
	private static MergeContext merge(FSTNode left, FSTNode base, FSTNode right) throws TextualMergeException {
		// indexes are necessary to a proper matching between nodes
		left.index = 0;
		base.index = 1;
		right.index = 2;

		MergeContext context = new MergeContext();
		context.leftTree = left;
		context.baseTree = base;
		context.rightTree = right;

		FSTNode mergeLeftBase = superimpose(left, base, null, context, SuperimpositionStep.Left_Base);
		FSTNode mergeLeftBaseRight = superimpose(mergeLeftBase, right, null, context,
				SuperimpositionStep.LeftBase_Right);

		removeRemainingBaseNodes(mergeLeftBaseRight, context);
		mergeMatchedContent(mergeLeftBaseRight, context);

		context.superImposedTree = mergeLeftBaseRight;

		return context;
	}

	/**
	 * Superimposes two given ASTs.
	 * 
	 * @param nodeA                representing the first tree
	 * @param nodeB                representing the second tree
	 * @param parent               node to be superimposed in or null when nodeA and
	 *                             nodeB are roots
	 * @param context
	 * @param isProcessingBaseTree
	 * @return superimposed tree
	 */
	private static FSTNode superimpose(FSTNode nodeA, FSTNode nodeB, FSTNonTerminal parent, MergeContext context,
			SuperimpositionStep step) {
		if (!nodeA.compatibleWith(nodeB))
			return null;

		// Setting up superimposed node.
		FSTNode result = nodeA.getShallowClone();
		result.index = nodeB.index;
		result.setParent(parent);

		if (areBothTerminals(nodeA, nodeB, parent)) {
			FSTTerminal terminalA = (FSTTerminal) nodeA;
			FSTTerminal terminalB = (FSTTerminal) nodeB;
			FSTTerminal terminalResult = (FSTTerminal) result;

			return superimposeTerminals(terminalA, terminalB, step, terminalResult);
		}

		else if (areBothNonTerminals(nodeA, nodeB)) {
			FSTNonTerminal nonTerminalA = (FSTNonTerminal) nodeA;
			FSTNonTerminal nonTerminalB = (FSTNonTerminal) nodeB;
			FSTNonTerminal nonTerminalResult = (FSTNonTerminal) result;

			return superimposeNonTerminals(nonTerminalA, nonTerminalB, context, step, nonTerminalResult);
		}

		return null;
	}

	private static boolean areBothTerminals(FSTNode nodeA, FSTNode nodeB, FSTNonTerminal parent) {
		return nodeA instanceof FSTTerminal && nodeB instanceof FSTTerminal;
	}

	private static boolean areBothNonTerminals(FSTNode nodeA, FSTNode nodeB) {
		return nodeA instanceof FSTNonTerminal && nodeB instanceof FSTNonTerminal;
	}

	private static FSTNode superimposeTerminals(FSTTerminal terminalA, FSTTerminal terminalB, SuperimpositionStep step,
			FSTTerminal result) {
		if (!terminalA.getMergingMechanism().equals("Default")) {

			String markedContent = markContributions(terminalA.getBody(), terminalB.getBody(), step, terminalA.index,
					terminalB.index);
			String markedPrefix = markContributions(terminalA.getSpecialTokenPrefix(),
					terminalB.getSpecialTokenPrefix(), step, terminalA.index, terminalB.index);

			result.setBody(markedContent);
			result.setSpecialTokenPrefix(markedPrefix);
		}
		return result;
	}

	private static FSTNode superimposeNonTerminals(FSTNonTerminal nonTerminalA, FSTNonTerminal nonTerminalB,
			MergeContext context, SuperimpositionStep step, FSTNonTerminal result) {
		addNewNodesFromLeftOrDeletedNodesFromRight(nonTerminalA, nonTerminalB, context, step, result);
		addDeletedNodesFromLeftOrNewNodesFromRight(nonTerminalA, nonTerminalB, context, step, result);
		return result;
	}

	/*
	 * For each of A's children, we check if it isn't present in B. If true, we add
	 * the child in the superimposed node in the correct index position and update the merge context.
	 */
	private static void addDeletedNodesFromLeftOrNewNodesFromRight(FSTNonTerminal nonTerminalA, FSTNonTerminal nonTerminalB,
			MergeContext context, SuperimpositionStep step, FSTNonTerminal result) {

		List<FSTNode> nonTerminalAChildren = nonTerminalA.getChildren();

		for (int i = 0; i < nonTerminalAChildren.size(); i++) {
			FSTNode childA = nonTerminalAChildren.get(i);

			if (!thereIsCorrespondentNode(nonTerminalB, childA)) { // is a new node from left, or a deleted base node in right
				FSTNode cloneA = clone(nonTerminalA, childA);

				/* Add the node to the superimposed tree in a correct index. */
				FSTNode childALeftNeighbour = getLeftNeighbourNode(nonTerminalAChildren, i);
				FSTNode childARightNeighbour = getRightNeighbourNode(nonTerminalAChildren, i);
				addNodeToNonTerminalNearNeighbour(cloneA, childALeftNeighbour, childARightNeighbour, result);

				/* Filling merge context. */
				if (step == SuperimpositionStep.Left_Base) { // node added by left in relation to base
					context.addedLeftNodes.add(cloneA);
				}

				else if (!context.addedLeftNodes.contains(cloneA)) { // node removed by right
					context.nodesDeletedByRight.add(cloneA);

					if (context.nodesDeletedByLeft.contains(cloneA)) { // node removed by both
						context.deletedBaseNodes.add(cloneA);
					}
				}
			}
		}
	}

	private static boolean thereIsCorrespondentNode(FSTNonTerminal nonTerminal, FSTNode node) {
		return nonTerminal.getCompatibleChild(node) != null;
	}

	/*
	 * For each of B's children, we check if it's present in A. If true, we recurse.
	 * Otherwise, we add it to the superimposed tree and update the merge context.
	 */
	private static void addNewNodesFromLeftOrDeletedNodesFromRight(FSTNonTerminal nonTerminalA, FSTNonTerminal nonTerminalB,
			MergeContext context, SuperimpositionStep step, FSTNonTerminal result) {

		for (FSTNode childB : nonTerminalB.getChildren()) {
			FSTNode childA = nonTerminalA.getCompatibleChild(childB);

			if (childA == null) { // means that a base node was deleted by left, or that a right node was added
				FSTNode cloneB = clone(nonTerminalB, childB);

				/* Add cloneB to the superimposed tree, but it needs to be removed later if it's a base node. */
				result.addChild(cloneB); 

				/* Filling merge context. */
				if (step == SuperimpositionStep.Left_Base) { // base node deleted by left
					context.nodesDeletedByLeft.add(cloneB);
				}

				else { // node added by right
					context.addedRightNodes.add(cloneB);
				}

			} else {
				updateIndexIfMinusOne(nonTerminalA, childA);
				updateIndexIfMinusOne(nonTerminalB, childB);

				if (step == SuperimpositionStep.LeftBase_Right && context.addedLeftNodes.contains(childA)) { // duplications
					context.addedRightNodes.add(childB);
				}

				result.addChild(superimpose(childA, childB, result, context, step));
			}
		}
	}

	private static FSTNode clone(FSTNonTerminal nonTerminal, FSTNode child) {
		FSTNode clone = child.getDeepClone();
		updateIndexIfMinusOne(nonTerminal, child);
		clone.index = child.index;
		return clone;
	}

	private static void updateIndexIfMinusOne(FSTNonTerminal nonTerminal, FSTNode child) {
		if (child.index == -1)
			child.index = nonTerminal.index;
	}

	/**
	 * After superimposition, the content of a matched node is the content of those
	 * that originated him (left,base,right) So, this methods indicates the origin
	 * (left,base or right) in node's body content.
	 * 
	 * @return node's body content marked
	 */
	private static String markContributions(String bodyA, String bodyB, SuperimpositionStep step, int indexA,
			int indexB) {
		if (bodyA.contains(SEMANTIC_MERGE_MARKER)) {
			return bodyA + bodyB;
		} else {
			if (step == SuperimpositionStep.Left_Base) {
				return SEMANTIC_MERGE_MARKER + bodyA + MERGE_SEPARATOR + bodyB + MERGE_SEPARATOR;
			} else {
				if (indexA == 0) {
					return SEMANTIC_MERGE_MARKER + bodyA + MERGE_SEPARATOR + MERGE_SEPARATOR + bodyB;
				} else {
					return SEMANTIC_MERGE_MARKER + MERGE_SEPARATOR + bodyA + MERGE_SEPARATOR + bodyB;
				}
			}
		}
	}

	/**
	 * After superimposition, base nodes supposed to be removed might remain. This
	 * method removes these nodes from the merged tree.
	 * 
	 * @param mergedTree
	 * @param context
	 */
	private static void removeRemainingBaseNodes(FSTNode mergedTree, MergeContext context) {
		boolean removed = false;
		if (!context.deletedBaseNodes.isEmpty()) {
			for (FSTNode loneBaseNode : context.deletedBaseNodes) {
				if (mergedTree == loneBaseNode) {
					FSTNonTerminal parent = (FSTNonTerminal) mergedTree.getParent();
					if (parent != null) {
						parent.removeChild(mergedTree);
						removed = true;
					}
				}
			}
			if (!removed && mergedTree instanceof FSTNonTerminal) {
				Object[] children = ((FSTNonTerminal) mergedTree).getChildren().toArray();
				for (Object child : children) {
					removeRemainingBaseNodes((FSTNode) child, context);
				}
			}
		}
	}

	/**
	 * After superimposition, the content of a matched node is the content of those
	 * that originated him (left,base,right). This method merges these parents'
	 * content. For instance, calling unstructured merge to merge methods' body and
	 * prefix. We use the tags from the method
	 * {@link #markContributions(String, String, boolean, int, int)} to guide this
	 * process.
	 * 
	 * @param node to be merged
	 * @throws TextualMergeException
	 */
	private static void mergeMatchedContent(FSTNode node, MergeContext context) throws TextualMergeException {
		if (node instanceof FSTNonTerminal) {
			for (FSTNode child : ((FSTNonTerminal) node).getChildren())
				mergeMatchedContent(child, context);
		} else if (node instanceof FSTTerminal) {

			/* Merging body. */
			if (((FSTTerminal) node).getBody().contains(SemistructuredMerge.MERGE_SEPARATOR)) {
				String mergedBodyContent = mergeBodyContent(node, context, ((FSTTerminal) node).getBody());
				((FSTTerminal) node).setBody(mergedBodyContent);
			}

			/* Merging prefix: possible comments. */
			if (((FSTTerminal) node).getSpecialTokenPrefix().contains(SemistructuredMerge.MERGE_SEPARATOR)) {
				String mergedPrefixContent = mergePrefixContent(node, context,
						((FSTTerminal) node).getSpecialTokenPrefix());
				((FSTTerminal) node).setSpecialTokenPrefix(mergedPrefixContent);
			}

		} else {
			System.err.println("Warning: node is neither non-terminal nor terminal!");
		}
	}

	private static String mergeBodyContent(FSTNode node, MergeContext context, String nodeField)
			throws TextualMergeException {
		Triple<String, String, String> contributionsContents = splitContributionsContents(nodeField);
		String leftContent = contributionsContents.getLeft().trim();
		String baseContent = contributionsContents.getMiddle().trim();
		String rightContent = contributionsContents.getRight().trim();

		identifyNodesEditedInOnlyOneVersion(node, context, leftContent, baseContent, rightContent);
		if(JFSTMerge.isMethodAndConstructorRenamingAndDeletionHandlerEnabled)
    		identifyPossibleNodesDeletionOrRenamings(node, context, leftContent, baseContent, rightContent);

		return JFSTMerge.textualMergeStrategy.merge(leftContent, baseContent, rightContent, JFSTMerge.isWhitespaceIgnored);
	}

	private static String mergePrefixContent(FSTNode node, MergeContext context, String nodeField)
			throws TextualMergeException {
		Triple<String, String, String> contributionsContents = splitContributionsContents(nodeField);
		return RenamingUtils.compareAndMerge(contributionsContents.getLeft(), contributionsContents.getMiddle(),
				contributionsContents.getRight());
	}

	private static Triple<String, String, String> splitContributionsContents(String nodeContent) {
		String[] splitContent = nodeContent.split(SemistructuredMerge.MERGE_SEPARATOR);
		String leftContent = splitContent[0].replace(SemistructuredMerge.SEMANTIC_MERGE_MARKER, "");
		String baseContent = (splitContent.length > 1) ? splitContent[1] : "";
		String rightContent = (splitContent.length > 2) ? splitContent[2] : "";
		return Triple.of(leftContent, baseContent, rightContent);
	}

	/**
	 * Verifies if a node was edited in only one of the revisions (left, or right),
	 * and fills the given merge context with this information.
	 * 
	 * @param node
	 * @param context
	 * @param leftContent
	 * @param baseContent
	 * @param rightContent
	 */
	private static void identifyNodesEditedInOnlyOneVersion(FSTNode node, MergeContext context, String leftContent,
			String baseContent, String rightContent) {
		String leftContenttrim = FilesManager.getStringContentIntoSingleLineNoSpacing(leftContent);
		String baseContenttrim = FilesManager.getStringContentIntoSingleLineNoSpacing(baseContent);
		String rightContenttrim = FilesManager.getStringContentIntoSingleLineNoSpacing(rightContent);
		if (!baseContenttrim.isEmpty()) {
			if (baseContenttrim.equals(leftContenttrim) && !rightContenttrim.equals(leftContenttrim)) {
				context.editedRightNodes.add(node);
			} else if (baseContenttrim.equals(rightContenttrim) && !leftContenttrim.equals(rightContenttrim)) {
				context.editedLeftNodes.add(node);
			}
		}
	}

	/**
	 * Verifies if a node was deleted/renamed in one of the revisions
	 * 
	 * @param node
	 * @param context
	 * @param leftContent
	 * @param baseContent
	 * @param rightContent
	 */
	private static void identifyPossibleNodesDeletionOrRenamings(FSTNode node, MergeContext context, String leftContent,
			String baseContent, String rightContent) {
		String leftContenttrim = FilesManager.getStringContentIntoSingleLineNoSpacing(leftContent);
		String baseContenttrim = FilesManager.getStringContentIntoSingleLineNoSpacing(baseContent);
		String rightContenttrim = FilesManager.getStringContentIntoSingleLineNoSpacing(rightContent);
		if (!baseContenttrim.isEmpty()) {
			if (!baseContenttrim.equals(leftContenttrim) && rightContenttrim.isEmpty()) {
				Pair<String, FSTNode> tuple = Pair.of(baseContent, node);
				context.possibleRenamedRightNodes.add(tuple);
			} else if (!baseContenttrim.equals(rightContenttrim) && leftContenttrim.isEmpty()) {
				Pair<String, FSTNode> tuple = Pair.of(baseContent, node);
				context.possibleRenamedLeftNodes.add(tuple);
			}
		}
	}

	/**
	 * Gets the node which comes before a given node (indexed by nodeIndex) in a
	 * given list of nodes If the node is the first element in the list, it returns
	 * null
	 * 
	 * @param nodes
	 * @param nodeIndex
	 * @return node's left neighbour or null
	 */
	private static FSTNode getLeftNeighbourNode(List<FSTNode> nodes, int nodeIndex) {
		boolean nodeHasLeftNeighbour = nodeIndex > 0;
		FSTNode leftNeighbour = null;

		if (nodeHasLeftNeighbour) {
			leftNeighbour = nodes.get(nodeIndex - 1);
		}

		return leftNeighbour;
	}

	/**
	 * Gets the node which comes after a given node (indexed by nodeIndex) in a
	 * given list of nodes If the node is the last element in the list, it returns
	 * null
	 * 
	 * @param nodes
	 * @param nodeIndex
	 * @return node's right neighbour or null
	 */
	private static FSTNode getRightNeighbourNode(List<FSTNode> nodes, int nodeIndex) {
		boolean nodeHasRightNeighbour = nodeIndex < nodes.size() - 1;
		FSTNode rightNeighbour = null;

		if (nodeHasRightNeighbour) {
			rightNeighbour = nodes.get(nodeIndex + 1);
		}

		return rightNeighbour;
	}

	/**
	 * Inserts a node into a non-terminal node by first trying to find a neighbour
	 * which was already inserted so the node can be inserted near such neighbour.
	 * In case a neighbour node is not one of non-terminal node's children, it just
	 * adds the node at the end of list.
	 * 
	 * @param node
	 * @param leftNeighbour
	 * @param rightNeighbour
	 * @param nonTerminal
	 */
	private static void addNodeToNonTerminalNearNeighbour(FSTNode node, FSTNode leftNeighbour, FSTNode rightNeighbour,
			FSTNonTerminal nonTerminal) {
		boolean hasFoundNeighbour = false;

		if (leftNeighbour != null) {
			int leftNeighbourIndex = findChildNodeIndex(nonTerminal, leftNeighbour);

			if (leftNeighbourIndex != -1) { // left neighbour found in nonTerminal
				nonTerminal.addChild(node, leftNeighbourIndex + 1); // add node after left neighbour
				hasFoundNeighbour = true;
			}
		}

		if (!hasFoundNeighbour && rightNeighbour != null) {
			int rightSiblingIndex = findChildNodeIndex(nonTerminal, rightNeighbour);

			if (rightSiblingIndex != -1) { // right neighbour found in nonTerminal
				nonTerminal.addChild(node, rightSiblingIndex); // add node before right neighbour
				hasFoundNeighbour = true;
			}
		}

		if (!hasFoundNeighbour) {
			nonTerminal.addChild(node); // add node at the end
		}
	}

	private static int findChildNodeIndex(FSTNonTerminal parentNode, FSTNode node) {
		return parentNode.getChildren().indexOf(node);
	}

}
