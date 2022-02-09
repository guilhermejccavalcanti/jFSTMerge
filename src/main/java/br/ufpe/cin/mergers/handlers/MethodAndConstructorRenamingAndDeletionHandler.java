package br.ufpe.cin.mergers.handlers;

import br.ufpe.cin.app.JFSTMerge;
import br.ufpe.cin.exceptions.TextualMergeException;
import br.ufpe.cin.mergers.handlers.renaming.RenamingHandler;
import br.ufpe.cin.mergers.handlers.renaming.RenamingHandlerFactory;
import br.ufpe.cin.mergers.util.MergeContext;
import br.ufpe.cin.mergers.util.RenamingUtils;
import br.ufpe.cin.mergers.util.Side;
import br.ufpe.cin.mergers.util.Traverser;
import de.ovgu.cide.fstgen.ast.FSTNode;
import de.ovgu.cide.fstgen.ast.FSTTerminal;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.javatuples.Quartet;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

/**
 * Renaming or deletions conflicts occur when one developer edits an element
 * that was renamed or deleted by another developer. Semistructured merge plain
 * algorithm is unable to detect such cases because it matches elements via
 * their identifiers; so if an element is renamed, the algorithm cannot match
 * the corresponding elements. This class handles such cases.
 * 
 * We classify renaming or deletion in three main cases: (1) Renaming without
 * body changes: one altered a method's signature only; (2) Renaming with body
 * changes: one altered a method's signature and body; (3) Deletion: one deleted
 * a method.
 *
 * @author Guilherme Cavalcanti (gjcc@cin.ufpe.br)
 * @author João Victor (jvsfc@cin.ufpe.br)
 * @author Giovanni Barros (gaabs@cin.ufpe.br)
 */
public final class MethodAndConstructorRenamingAndDeletionHandler implements ConflictHandler {
	private RenamingHandler renamingHandler;

	public MethodAndConstructorRenamingAndDeletionHandler() {
		this.renamingHandler = RenamingHandlerFactory.getHandler(JFSTMerge.renamingStrategy);
	}

	@Override
	public void handle(MergeContext context) throws TextualMergeException {

		/*
		 * 1. Identification Step: Traverses the base tree looking for renamed or
		 * deleted nodes, filling the renamedWithoutBodyChanges and
		 * deletedOrRenamedWithoutBodyChanges attributes.
		 */
		identifyRenamingOrDeletionNodes(context);

		/*
		 * 2. Match Step: For each of the base renamed nodes, we find the nodes from the
		 * left, right and merge trees involved in this renaming. If we can't find the
		 * left or the right node, we treat them as deleted.
		 */
		List<Quartet<FSTNode, FSTNode, FSTNode, FSTNode>> renamingMatches = retrieveRenamingMatches(context);

		/*
		 * 3. Handling Step: For each of the matches, we simply run a decision tree to
		 * decide what do to with them, based on their renaming types.
		 */
		handle(context, renamingMatches);

	}

	private void identifyRenamingOrDeletionNodes(MergeContext context) {
		List<FSTTerminal> terminals = Traverser.collectTerminals(context.baseTree);
		terminals.stream().filter(terminal -> RenamingUtils.isMethodOrConstructorNode(terminal))
		.forEach(terminal -> {
			identifyRenamingOrDeletion(Side.LEFT, context, terminal, context.leftTree, context.addedLeftNodes);
			identifyRenamingOrDeletion(Side.RIGHT, context, terminal, context.rightTree, context.addedRightNodes);
		});
	}

	private void identifyRenamingOrDeletion(Side contribution, MergeContext context, FSTNode node,
			FSTNode contributionTree, List<FSTNode> addedNodes) {

		if (isRenamingWithoutBodyChanges(node, contributionTree, addedNodes)) {
			context.renamedWithoutBodyChanges.add(Pair.of(contribution, node));
		}

		if (isDeletionOrRenamingWithBodyChanges(node, contributionTree, addedNodes)) {
			context.deletedOrRenamedWithBodyChanges.add(Pair.of(contribution, node));
		}
	}

	private boolean isRenamingWithoutBodyChanges(FSTNode node, FSTNode contributionTree, List<FSTNode> addedNodes) {
		return !isInContribution(node, contributionTree) && matchesWithEqualBody(node, addedNodes);
	}

	private boolean isDeletionOrRenamingWithBodyChanges(FSTNode node, FSTNode contributionTree,
			List<FSTNode> addedNodes) {
		return !isInContribution(node, contributionTree) && !matchesWithEqualBody(node, addedNodes);
	}

	private boolean isInContribution(FSTNode node, FSTNode contributionTree) {
		return Traverser.isInTree(node.getType(), node.getName(), contributionTree);
	}

	private boolean matchesWithEqualBody(FSTNode baseNode, List<FSTNode> addedNodes) {
		return addedNodes.stream().filter(node -> node instanceof FSTTerminal)
				.anyMatch(node -> RenamingUtils.haveEqualBodyModuloWhitespace(baseNode, node));
	}

	private List<Quartet<FSTNode, FSTNode, FSTNode, FSTNode>> retrieveRenamingMatches(MergeContext context) {
		List<Pair<Side, FSTNode>> renamedNodes = unionRenamedNodes(context.renamedWithoutBodyChanges,
				context.deletedOrRenamedWithBodyChanges);
		return renamedNodes.stream().map(pair -> retrieveScenarioNodes(context, pair.getRight()))
				.filter(triple -> triple.getLeft() != null || triple.getRight() != null)
				.map(triple -> retrieveScenarioNodesWithMergeNode(context, triple)).distinct()
				.collect(Collectors.toList());
	}

	private Triple<FSTNode, FSTNode, FSTNode> retrieveScenarioNodes(MergeContext context, FSTNode baseNode) {
		return Triple.of(getMostAccurateMatch(baseNode, context.leftTree), baseNode,
				getMostAccurateMatch(baseNode, context.rightTree));
	}

	private Quartet<FSTNode, FSTNode, FSTNode, FSTNode> retrieveScenarioNodesWithMergeNode(MergeContext context,
			Triple<FSTNode, FSTNode, FSTNode> scenarioNodes) {
		FSTNode leftNode = scenarioNodes.getLeft();
		FSTNode baseNode = scenarioNodes.getMiddle();
		FSTNode rightNode = scenarioNodes.getRight();

		if (leftNode != null)
			return Quartet.with(leftNode, baseNode, rightNode,
					getCorrespondentNode(leftNode, context.superImposedTree));
		else
			return Quartet.with(leftNode, baseNode, rightNode,
					getCorrespondentNode(rightNode, context.superImposedTree));
	}

	private FSTNode getCorrespondentNode(FSTNode node, FSTNode tree) {
		return Traverser.retrieveNodeFromTree(node, tree);
	}

	private FSTNode getMostAccurateMatch(FSTNode node, FSTNode contributionTree) {
		List<FSTTerminal> terminals = Traverser.collectTerminals(contributionTree);
		if (RenamingUtils.isAbstractMethod(node)) {
			return getMostAccurateMatchForAbstractMethods(node, terminals);
		} else {
			return getMostAccurateMatchForNonAbstractMethods(node, terminals);
		}
	}

	private FSTNode getMostAccurateMatchForAbstractMethods(FSTNode node, List<FSTTerminal> terminals) {
		FSTNode match = tryFindFirstMatch(terminals, node, (n1, n2) -> RenamingUtils.haveEqualSignature(n1, n2));
		if (match == null) {
			match = tryFindFirstMatch(terminals, node, (n1, n2) -> RenamingUtils.haveEqualSignatureButName(n1, n2)
					|| RenamingUtils.haveEqualSignatureButArguments(n1, n2));
		}
		return match;
	}

	private FSTNode getMostAccurateMatchForNonAbstractMethods(FSTNode node, List<FSTTerminal> terminals) {
		FSTNode match = tryFindFirstMatch(terminals, node, (n1, n2) -> RenamingUtils.haveEqualSignature(n1, n2));
		if (match == null) {
			match = tryFindFirstMatch(terminals, node, (n1, n2) -> RenamingUtils.haveEqualBodyModuloWhitespace(n1, n2));
		}
		if (match == null) {
			match = tryFindFirstMatch(terminals, node,
					(n1, n2) -> RenamingUtils.haveSimilarBodyModuloWhitespace(n1, n2)
					&& (RenamingUtils.haveEqualSignatureButName(n1, n2)
							|| RenamingUtils.haveEqualSignatureButArguments(n1, n2)));
		}
		if (match == null) {
			match = tryFindFirstMatch(terminals, node,
					(n1, n2) -> RenamingUtils.oneContainsTheBodyFromTheOther(n1, n2));
		}
		return match;
	}

	private FSTNode tryFindFirstMatch(List<FSTTerminal> candidates, FSTNode node,
			BiPredicate<FSTNode, FSTNode> predicate) {
		for (FSTNode candidate : candidates) {
			if (RenamingUtils.isMethodOrConstructorNode(candidate) && predicate.test(node, candidate)) {
				return candidate;
			}
		}
		return null;
	}

	private List<Pair<Side, FSTNode>> unionRenamedNodes(List<Pair<Side, FSTNode>> renamedWithoutBodyChanges,
			List<Pair<Side, FSTNode>> deletedOrRenamedWithBodyChanges) {
		List<Pair<Side, FSTNode>> unionNodes = new ArrayList<>();
		renamedWithoutBodyChanges.stream().forEach(pair -> unionNodes.add(pair));
		deletedOrRenamedWithBodyChanges.stream().forEach(pair -> unionNodes.add(pair));
		return unionNodes;
	}

	private void handle(MergeContext context, List<Quartet<FSTNode, FSTNode, FSTNode, FSTNode>> renamingMatches)
			throws TextualMergeException {
		for (Quartet<FSTNode, FSTNode, FSTNode, FSTNode> match : renamingMatches)
			renamingHandler.handle(context, match);
	}

}
