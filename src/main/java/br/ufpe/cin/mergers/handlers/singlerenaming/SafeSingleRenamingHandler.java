package br.ufpe.cin.mergers.handlers.singlerenaming;

import br.ufpe.cin.files.FilesManager;
import br.ufpe.cin.mergers.util.MergeConflict;
import br.ufpe.cin.mergers.util.MergeContext;
import br.ufpe.cin.mergers.util.RenamingUtils;
import br.ufpe.cin.mergers.util.Side;
import de.ovgu.cide.fstgen.ast.FSTNode;
import de.ovgu.cide.fstgen.ast.FSTTerminal;

import java.util.List;

public class SafeSingleRenamingHandler implements SingleRenamingHandler {
    public void handle(MergeContext context, String baseContent, FSTNode conflictNode,
                       List<FSTNode> addedNodes, Side renamingSide) {

        String conflictNodeContent = ((FSTTerminal) conflictNode).getBody();
        MergeConflict mergeConflict = FilesManager.extractMergeConflicts(conflictNodeContent).get(0);
        String oppositeSideNodeContent = RenamingUtils.getMergeConflictContentOfOppositeSide(mergeConflict, renamingSide);

        if (RenamingUtils.hasUnstructuredMergeConflict(context, baseContent)) {
            String possibleRenamingContent = RenamingUtils.getMostSimilarNodeContent(baseContent, conflictNode, addedNodes);
            RenamingUtils.generateRenamingConflict(context, conflictNodeContent, possibleRenamingContent, oppositeSideNodeContent, renamingSide);
        } else {
            ((FSTTerminal) conflictNode).setBody(oppositeSideNodeContent);
        }
    }
}