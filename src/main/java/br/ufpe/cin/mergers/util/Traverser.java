package br.ufpe.cin.mergers.util;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import de.ovgu.cide.fstgen.ast.FSTNode;
import de.ovgu.cide.fstgen.ast.FSTNonTerminal;
import de.ovgu.cide.fstgen.ast.FSTTerminal;

/**
 * A class for traversing trees (FSTNode) involved in a merge.
 */
public class Traverser {

    /**
     * Compares all nodes in {@code tree} to {@code node} by
     * reference to find out if this exact node is in the tree
     * 
     * @param node
     * @param tree
     * @return true if node is in tree, false otherwise
    */
    public static boolean isInTree(FSTNode node, FSTNode tree) {
        List<FSTNode> nodes = collectNodes(tree);
        for (FSTNode n: nodes)
            if (n == node)
                return true;

        return false;
    }

    /**
     * Compares all nodes types and names in {@code tree} to a
     * given node's {@code type} and {@code name} to find out if
     * there is a node in {@code tree} matching given node's parameters
     * 
     * @param type
     * @param name
     * @param tree
     * @return true if a match was found, false otherwise
     */
    public static boolean isInTree(String type, String name, FSTNode tree) {
        List<FSTNode> nodes = collectNodes(tree);
        for (FSTNode node: nodes)
            if (node.getType().equals(type) && node.getName().equals(name))
                return true;

        return false;
    }

    public static List<FSTTerminal> collectTerminals(FSTNode tree) {
        return collectNodes(tree).stream()
            .filter(node -> node instanceof FSTTerminal)
            .map(FSTTerminal.class::cast)
            .collect(Collectors.toList());
    }

    public static FSTNode retrieveNodeFromTree(FSTNode node, FSTNode tree) {
        if (node == null) return null;
        
        List<FSTNode> nodes = collectNodes(tree);
        for (FSTNode treeNode : nodes) {
            if(treeNode.getName().equals(node.getName()))
                return treeNode;
        }
        return null;
    }
    
    public static boolean removeNode(FSTNode node, FSTNode tree) {
        if(tree instanceof FSTTerminal && tree.equals(node)) {
            ((FSTTerminal) tree).getParent().removeChild(node);
            return true;
        } else if (tree instanceof FSTNonTerminal) {
            for (FSTNode child : ((FSTNonTerminal) tree).getChildren())
                if(removeNode(node, child))
                    return true;
        }
        return false;
    }

    private static List<FSTNode> collectNodes(FSTNode tree) {
       return collectNodes(new ArrayList<FSTNode>(), tree);
    }

    private static List<FSTNode> collectNodes(List<FSTNode> nodes, FSTNode tree) {
        if(tree instanceof FSTTerminal)
            nodes.add(tree);
        else if(tree instanceof FSTNonTerminal) {
            nodes.add(tree);
            for(FSTNode child : ((FSTNonTerminal) tree).getChildren())
                collectNodes(nodes, child);
        } else {
            System.err.println("Warning: node is neither non-terminal nor terminal!");
        }
        return nodes;
    }

    private static boolean containsNode(List<FSTTerminal> nodes, FSTNode node) {
        return nodes.stream().anyMatch(testNode -> testNode.equals(node) && testNode.getBody().equals(((FSTTerminal) node).getBody()));
    }

    
}