package br.ufpe.cin.mergers.handlers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import br.ufpe.cin.app.JFSTMerge;
import br.ufpe.cin.exceptions.TextualMergeException;
import br.ufpe.cin.files.FilesManager;
import br.ufpe.cin.mergers.util.MergeConflict;
import br.ufpe.cin.mergers.util.MergeContext;
import de.ovgu.cide.fstgen.ast.FSTNode;
import de.ovgu.cide.fstgen.ast.FSTTerminal;

/**
 * As in semistructured merge the ID is used for matching the nodes and initialization blocks don't have it, during
 * the superimposition of these blocks they are duplicated. This handler matches the nodes mainly by the level of
 * content insertion compared to the blocks on base and checking for possible dependency between blocks.
 * 
 *  @author Alice Borner
 *  
 */
public class InitializationBlocksHandlerMultipleBlocks implements ConflictHandler {
    
	// identifiers and regex 
    private final static String INITIALIZATION_BLOCK_IDENTIFIER = "InitializerDecl";	
    private final static String LINE_BREAK_REGEX = "\\r?\\n\\t?";
    private final static String VAR_DECLARATION_ASSIGNMENT_REGEX = "^([a-zA-Z_$][a-zA-Z_$0-9]*)? "
    		+ "*([a-zA-Z_$][a-zA-Z_$0-9]*) *(=|\\.set) *(.*)?;$";
    
    private final static String CONFLICT_MARKER = "<<<<<<<";   
    private final static double INSERTION_THRESHOLD = 0.7;
    private final static double SIMILARITY_THRESHOLD = 0.5;
    
	public void handle(MergeContext context) throws TextualMergeException {
		
        List<FSTNode> leftNodes = findInitializationBlocks(context.addedLeftNodes);
        List<FSTNode> rightNodes = findInitializationBlocks(context.addedRightNodes);
        List<FSTNode> baseNodes = findInitializationBlocks(context.deletedBaseNodes);

        Map<FSTNode, FSTNode> baseLeftEditedNodesMap = selectEditedNodes(leftNodes, baseNodes);
        Map<FSTNode, FSTNode> baseRightEditedNodesMap = selectEditedNodes(rightNodes, baseNodes);

        // pair of nodes lists added by left and right respectively
        Pair<List<FSTNode>, List<FSTNode>> addedNodes = selectAddedNodes(leftNodes, baseNodes, rightNodes,
        		baseLeftEditedNodesMap.values(), baseRightEditedNodesMap.values());
       
        // pair of nodes lists deleted by left and right respectively
        Pair<List<FSTNode>, List<FSTNode>> deletedNodes = selectDeletedNodes(leftNodes, baseNodes, rightNodes,
        		baseLeftEditedNodesMap.keySet(), baseRightEditedNodesMap.keySet());
        
    	for(FSTNode baseNode : baseNodes) {
    		
    		FSTNode leftNode = baseLeftEditedNodesMap.get(baseNode);
    		FSTNode rightNode = baseRightEditedNodesMap.get(baseNode);
    		
    		mergeContentAndUpdateAST(leftNode, baseNode, rightNode, context, deletedNodes);
    	}
    	
    	Map<FSTNode, List<FSTNode>> commonVarsNodesMap = getCommonAccessGlobalVariablesNodes(addedNodes);
    	if(!commonVarsNodesMap.isEmpty()) {
    		// there are common global variables being used by left and right added blocks
    		mergeDependentAddedNodesAndUpdateAST(context, commonVarsNodesMap);
    	}
    	
    	if(!addedNodes.getLeft().isEmpty() && !addedNodes.getRight().isEmpty())
    		mergeAddedNodesAndUpdateAST(context, addedNodes);
    }
	
    private Map<FSTNode, FSTNode> defineEditedNodes(List<FSTNode> addedCandidates,
    		List<FSTNode> deletedCandidates) {
    	
    	Map<FSTNode, FSTNode> baseEditedNodesMap = new HashMap<FSTNode, FSTNode>();

    	for(FSTNode node : addedCandidates) {
    		
    		Pair<FSTNode, Double> maxInsertionPair = getMaxInsertionLevelNode(node, deletedCandidates);
    		Pair<FSTNode, Double> maxSimilarityPair = getMaxSimilarityNode(node, deletedCandidates);
    		FSTNode baseNode = getBaseNode(maxInsertionPair, maxSimilarityPair);
    		
    		if(baseNode != null && (maxInsertionPair.getValue() > INSERTION_THRESHOLD ||
    				maxSimilarityPair.getValue() > SIMILARITY_THRESHOLD)) {
    			baseEditedNodesMap.put(baseNode, node);
    		}
    	}
    	
    	return baseEditedNodesMap;
    }

	private void mergeContentAndUpdateAST(FSTNode leftNode, FSTNode baseNode, FSTNode rightNode, MergeContext context, 
			Pair<List<FSTNode>,List<FSTNode>> deletedNodes) throws TextualMergeException {
		
		String baseContent = ((FSTTerminal) baseNode).getBody();
		String leftContent = getNodeBody(leftNode);
		String rightContent = getNodeBody(rightNode);

		if(leftNode != null && rightNode != null) {
			// both branches edited the node
			
		    String mergedContent = JFSTMerge.textualMergeStrategy.merge(leftContent, baseContent, rightContent, 
					JFSTMerge.isWhitespaceIgnored);
		    
            FilesManager.findAndReplaceASTNodeContent(context.superImposedTree, leftContent, mergedContent);
            FilesManager.findAndDeleteASTNode(context.superImposedTree, rightContent);
            
            if (mergedContent.contains(CONFLICT_MARKER)) {
            	context.initializationBlocksConflicts++;
            }
            
        } else if(leftNode == null && rightNode == null) {
        	// any branch edited the node, delete one of the nodes content to don't have duplicates
            FilesManager.findAndDeleteASTNode(context.superImposedTree, baseContent.trim());
		} else {
			// one of the branches edited or deleted the node
			if (leftNode == null) {
				// left deleted or right edited the node 
				List<FSTNode> leftDeletedNodes = deletedNodes.getLeft();
				mergeDeletedEditedContentAndUpdateAST(context, baseNode, leftDeletedNodes, rightNode, true);
			}
			
			if (rightNode == null) {  
				// right deleted or left edited the node 
				List<FSTNode> rightDeletedNodes = deletedNodes.getRight();
				mergeDeletedEditedContentAndUpdateAST(context, baseNode, rightDeletedNodes, leftNode, false);
			}
        }
	}

	private void mergeAddedNodesAndUpdateAST(MergeContext context, Pair<List<FSTNode>,List<FSTNode>> addedNodes) {
		for(FSTNode leftAddedNode : addedNodes.getLeft()) {
			for(FSTNode rightAddedNode : addedNodes.getRight()) {
				String leftContent = ((FSTTerminal) leftAddedNode).getBody();
				String rightContent = ((FSTTerminal) rightAddedNode).getBody(); 
				
				// added nodes are similar, then one must be deleted so it's not duplicated
				if(leftContent.equals(rightContent))
		            FilesManager.findAndDeleteASTNode(context.superImposedTree, rightContent);
			}
		}
	}

	private Map<FSTNode, FSTNode> selectEditedNodes(List<FSTNode> branchNodes, List<FSTNode> baseNodes) {
    	
    	// Finding deleted candidates
    	List<FSTNode> branchDeletedCandidates = removeContainedNodesFromList(baseNodes, branchNodes);

    	// Finding added candidates
    	List<FSTNode> branchAddedCandidates = removeContainedNodesFromList(branchNodes, baseNodes);
    	
    	// Defining edited nodes by similarity and/or insertion level
    	Map<FSTNode, FSTNode> branchEditedNodes = defineEditedNodes(branchAddedCandidates,
    			branchDeletedCandidates);
    			
    	return branchEditedNodes;
    }
	
	private Pair<List<FSTNode>, List<FSTNode>> selectDeletedNodes(List<FSTNode> leftNodes, List<FSTNode> baseNodes,
			List<FSTNode> rightNodes, Collection<FSTNode> leftEditedNodes, Collection<FSTNode> rightEditedNodes) {
		
		// Finding deleted candidates
    	List<FSTNode> leftDeletedCandidates = removeContainedNodesFromList(baseNodes, leftNodes);
    	List<FSTNode> rightDeletedCandidates = removeContainedNodesFromList(baseNodes, rightNodes);
    	
      	// Defining deleted nodes removing edited ones from the candidates list
    	List<FSTNode> leftDeletedNodes = removeContainedNodesFromList(leftDeletedCandidates, leftEditedNodes);
    	List<FSTNode> rightDeletedNodes = removeContainedNodesFromList(rightDeletedCandidates, rightEditedNodes);
    	
    	return Pair.of(leftDeletedNodes, rightDeletedNodes);
	}
	
	private Pair<List<FSTNode>, List<FSTNode>> selectAddedNodes(List<FSTNode> leftNodes, List<FSTNode> baseNodes,
			List<FSTNode> rightNodes, Collection<FSTNode> leftEditedNodes, Collection<FSTNode> rightEditedNodes) {
    	
		// Finding added candidates
    	List<FSTNode> leftAddedCandidates = removeContainedNodesFromList(leftNodes, baseNodes);
    	List<FSTNode> rightAddedCandidates = removeContainedNodesFromList(rightNodes, baseNodes);
    	
     	// Defining added nodes removing edited ones from the candidates list
    	List<FSTNode> leftAddedNodes = removeContainedNodesFromList(leftAddedCandidates, leftEditedNodes);
    	List<FSTNode> rightAddedNodes = removeContainedNodesFromList(rightAddedCandidates, rightEditedNodes);
    	
    	return Pair.of(leftAddedNodes, rightAddedNodes);
	}
	
	private void mergeDeletedEditedContentAndUpdateAST(MergeContext context, FSTNode baseNode,
			List<FSTNode> deletedNodes, FSTNode node, boolean isLeftNode) 
					throws TextualMergeException {
		
		String baseContent = ((FSTTerminal) baseNode).getBody();
		String editedNodeContent = ((FSTTerminal) node).getBody();
		String otherNodeContent = baseContent;
		
		if(containsNode(deletedNodes, baseNode)) {
            FilesManager.findAndDeleteASTNode(context.superImposedTree, baseContent);
            otherNodeContent = "";
    	}
		
		String mergedContent;
		
		// order of parameters changes depending on which branch changes/deleted the node
		if(isLeftNode) {
			mergedContent = JFSTMerge.textualMergeStrategy.merge(otherNodeContent, baseContent, editedNodeContent, 
					JFSTMerge.isWhitespaceIgnored);
		} else {
			mergedContent = JFSTMerge.textualMergeStrategy.merge(editedNodeContent, baseContent, otherNodeContent, 
					JFSTMerge.isWhitespaceIgnored);
		}
		
		FilesManager.findAndReplaceASTNodeContent(context.superImposedTree, editedNodeContent, mergedContent);
		FilesManager.findAndDeleteASTNode(context.superImposedTree, otherNodeContent);

		if (mergedContent != null && mergedContent.contains(CONFLICT_MARKER)) {
			context.initializationBlocksConflicts++;
		}
	}

	private Map<FSTNode,List<FSTNode>> getCommonAccessGlobalVariablesNodes(Pair<List<FSTNode>,
			List<FSTNode>> addedNodes) {
		
		Map<FSTNode,List<FSTNode>> commonVarsNodesMap = new HashMap<>();
		List<FSTNode> leftAddedNodes = addedNodes.getLeft();
		List<FSTNode> rightAddedNodes = addedNodes.getRight();
 
		for(FSTNode leftNode : leftAddedNodes) {
			
			String leftContent = ((FSTTerminal) leftNode).getBody();
			Set<String> leftGlobalVariables = getGlobalVariables(leftContent);

			for(FSTNode rightNode : rightAddedNodes) {
				
				String rightContent = ((FSTTerminal) rightNode).getBody();
				Set<String> rightGlobalVariables = getGlobalVariables(rightContent);
			    
				List<String> commmonVars = leftGlobalVariables.stream().filter(c -> rightGlobalVariables.contains(c))
						.collect(Collectors.toList());
		
				if(!commmonVars.isEmpty()) {
					// if left added node uses the same global variable as the right added node
					if(!getNodeFromMap(leftNode, commonVarsNodesMap).isEmpty()) {
						getNodeFromMap(leftNode, commonVarsNodesMap).add(rightNode);
					} else {
						List<FSTNode> rightNodes = new ArrayList<FSTNode>();
						rightNodes.add(rightNode);
						commonVarsNodesMap.put(leftNode, rightNodes);
					}
				}
			}
		}
		
       return commonVarsNodesMap;
	}
	
	private List<FSTNode> getNodeFromMap(FSTNode node, Map<FSTNode,List<FSTNode>> nodesMap) {
		
		FSTTerminal terminalNode = (FSTTerminal) node;
		
		return nodesMap.keySet().stream().filter(n -> ((FSTTerminal) n).getBody().
				equals(terminalNode.getBody())).collect(Collectors.toList());
	}
	
	private Set<String> getGlobalVariables(String nodeContent) {
		
		List<String> nodeContentLines = Arrays.asList(nodeContent.split(LINE_BREAK_REGEX));
		
		/* 
		 * Compiles the regex for var assignment or declaration. 
		 *  There are 4 groups in the regex.
		 *  group(1) - variable type, e.g.: int, double
		 *  group(2) - variable name
		 *  group(3) - assignment (=) or call of set method
		 *  group(4) - variable value
		 */
		Pattern varPattern = Pattern.compile(VAR_DECLARATION_ASSIGNMENT_REGEX);

		Set<String> localVars = findLocalVars(nodeContentLines, varPattern);
		
		Set<String> nodeGlobalVars = findGlobalVars(nodeContentLines, varPattern, localVars);
		
		return nodeGlobalVars;
	}

	private Set<String> findGlobalVars(List<String> nodeContentLines, Pattern varPattern,
			Set<String> localVars) {
		
		Set<String> nodeGlobalVars = new HashSet<String>();

		for(String line : nodeContentLines) {
			
			Matcher varAssignmentMatcher = varPattern.matcher(line.trim());
			String varName = varAssignmentMatcher.matches() ? varAssignmentMatcher.group(2) : "";
			String varAssignedValue = varAssignmentMatcher.matches() ? varAssignmentMatcher.group(4) : "";

			if(!varName.isEmpty() && !localVars.contains(varName)) {
				nodeGlobalVars.add(varName);
			}
			
			if(!varAssignedValue.isEmpty() && isVariable(varAssignedValue) && !localVars.contains(varAssignedValue)) {
				nodeGlobalVars.add(varAssignedValue);
			}
		}
		
		return nodeGlobalVars;
	}

	private Set<String> findLocalVars(List<String> nodeContentLines, Pattern varPattern) {
		Set<String> localVars = new HashSet<String>();
		
		for(String line : nodeContentLines) {
			
			Matcher localVarMatcher = varPattern.matcher(line.trim());
			String varType = localVarMatcher.matches() ? localVarMatcher.group(1) : "";
			
			if(varType != null && !varType.isEmpty()) {
				String varName = localVarMatcher.group(2);
				localVars.add(varName);
			}
		}
		
		return localVars;
	}
	
	private boolean isVariable(String value) {
		
		Pattern varPattern = Pattern.compile("[a-zA-Z_$][a-zA-Z_$0-9]*");
		Matcher varMatcher = varPattern.matcher(value);
		
		return varMatcher.matches();
	}
	
	private void mergeDependentAddedNodesAndUpdateAST(MergeContext context, Map<FSTNode, List<FSTNode>>
		commonVarsNodesMap) throws TextualMergeException {
		
		for(FSTNode leftNode : commonVarsNodesMap.keySet()) {
			
			String leftNodeContent = (leftNode != null) ? ((FSTTerminal) leftNode).getBody() : "";
			StringBuffer rightConflictContent = new StringBuffer();
			for(FSTNode rightNode : commonVarsNodesMap.get(leftNode)) {
				String rightContent = (rightNode != null) ? ((FSTTerminal) rightNode).getBody() : "";
		    	String nodeBody = getBodyInitializationBlock(rightContent);
				rightConflictContent.append(nodeBody);
				FilesManager.findAndDeleteASTNode(context.superImposedTree, rightContent);
			}
			
	    	String leftConflictContent = getBodyInitializationBlock(leftNodeContent);
	    	
	    	StringBuffer staticBlock = new StringBuffer("static {");
	    	MergeConflict mergeConflict = new MergeConflict(leftConflictContent, "", rightConflictContent.toString(), "conflicting static blocks");
			staticBlock.append(mergeConflict.toString() + "\n}");
			
			FilesManager.findAndReplaceASTNodeContent(context.superImposedTree, leftNodeContent, staticBlock.toString());
			context.initializationBlocksConflicts++;
		}
	}

	private List<FSTNode> removeContainedNodesFromList(Collection<FSTNode> nodesList, 
    		Collection<FSTNode> nodesToCheck) {
    	
    	return nodesList.stream().filter(node -> !containsNode(nodesToCheck, node)).collect(Collectors.toList());
    }
    
    private boolean containsNode(Collection<FSTNode> nodes, FSTNode node) {
    	
    	for(FSTNode listNode : nodes) {
    		
    		String listNodeContent = getNodeBody(listNode);
            String nodeContent = getNodeBody(node);

            if(listNodeContent.equals(nodeContent))
            	return true;
    	}
    	
    	return false;
    }

	private String getNodeBody(FSTNode node) {
		return (node != null) ? ((FSTTerminal) node).getBody().trim() : "";
	}
  
    private List<FSTNode> findInitializationBlocks(List<FSTNode> nodes) {
       
    	List<FSTNode> listOfInitBlocks = nodes.stream()
                .filter(p -> p.getType().equals(INITIALIZATION_BLOCK_IDENTIFIER))
                .collect(Collectors.toList());
    	
    	List<FSTNode> listWithoutDuplicates = getListWithoutDuplicates(listOfInitBlocks);

    	return listWithoutDuplicates;
    }
    
    private List<FSTNode> getListWithoutDuplicates(List<FSTNode> listOfBlocks) {
    	List<FSTNode> listWithoutDuplicates = new ArrayList<FSTNode>();
    	
    	for(FSTNode node : listOfBlocks) {
    		if(!containsNode(listWithoutDuplicates, node))
    			listWithoutDuplicates.add(node);
    	}
    	
    	return listWithoutDuplicates;
    }
    
    private static Pair<FSTNode, Double> getMaxInsertionLevelNode(FSTNode node, List<FSTNode> nodes) {
    	
    	Map<FSTNode, Double> nodesInsertionLevelMap = new HashMap<>();
    	String nodeBody = ((FSTTerminal) node).getBody();
    	String nodeLines = getBodyInitializationBlock(nodeBody);
    	List<String> splitNodeContent = Arrays.asList(nodeLines.split(LINE_BREAK_REGEX));

    	calculateInsertionLevels(nodes, nodesInsertionLevelMap, splitNodeContent);
    	
    	FSTNode nodeMaxValue = getNodeWithHighestValue(nodesInsertionLevelMap);
    	
    	return Pair.of(nodeMaxValue, nodesInsertionLevelMap.get(nodeMaxValue));
    }

	private static void calculateInsertionLevels(List<FSTNode> nodes, Map<FSTNode, Double> nodesInsertionLevelMap,
			List<String> splitNodeContent) {
		
		for(FSTNode pairNode : nodes) {
        	
    		String pairNodeBody = ((FSTTerminal) pairNode).getBody();
        	String pairNodeLines = getBodyInitializationBlock(pairNodeBody);
        	List<String> splitPairNodeContent = Arrays.asList(pairNodeLines.split(LINE_BREAK_REGEX));
        	
        	double numOfInsertions = 0;
        	for(String content : splitNodeContent) {
        		for(String pairContent : splitPairNodeContent) {
        			if(!content.trim().isEmpty() && !pairContent.trim().isEmpty()
        					&& content.trim().equals(pairContent.trim()))
        				numOfInsertions++;
        		}
        	}
        	
        	double insertionLevel = numOfInsertions / splitPairNodeContent.size();
        	
        	nodesInsertionLevelMap.put(pairNode, insertionLevel);
    	}
	}
    
    private static Pair<FSTNode, Double> getMaxSimilarityNode(FSTNode node, List<FSTNode> nodes) {
    	
    	String nodeContent = ((FSTTerminal) node).getBody();
    	Map<FSTNode, Double> nodesSimilarityLevelMap = new HashMap<>();
    	
    	for(FSTNode pairNode : nodes) {
    		
        	String pairNodeContent = ((FSTTerminal) pairNode).getBody();
        	double similarity = FilesManager.computeStringSimilarity(nodeContent, pairNodeContent);
        	nodesSimilarityLevelMap.put(pairNode, similarity);
    	}
    	
    	FSTNode nodeMaxValue = getNodeWithHighestValue(nodesSimilarityLevelMap);
    	
    	return Pair.of(nodeMaxValue, nodesSimilarityLevelMap.get(nodeMaxValue));
    }
    
    private static FSTNode getNodeWithHighestValue(Map<FSTNode, Double> nodesMap) {
    	
    	if(nodesMap.entrySet().isEmpty())
    		return null;
    	
    	return Collections.max(nodesMap.entrySet(), Comparator.comparingDouble(Map.Entry::getValue))
    			.getKey();
    }
    
    private FSTNode getBaseNode(Pair<FSTNode, Double> maxInsertionPair, Pair<FSTNode, Double> maxSimilarityPair) {
    	
    	FSTNode baseNode;
    	
    	if(maxInsertionPair.getKey() != null && maxInsertionPair.getValue() != 0) {
    		baseNode = maxInsertionPair.getKey();
    	} else {
    		baseNode =  maxSimilarityPair.getKey();
    	}
    	
    	return baseNode;
    }
	
	private static String getBodyInitializationBlock(String source) {
	   
	    return removeContentStartEndInitializationBlock(source);
    }

    private static String removeContentStartEndInitializationBlock(String source) {
	    
		String bodyWithoutStatic = StringUtils.removeStart(source, "static").trim();
	    String body = StringUtils.removeStart(bodyWithoutStatic, "{");
		
		return StringUtils.removeEnd(body, "}"); 
    }
}