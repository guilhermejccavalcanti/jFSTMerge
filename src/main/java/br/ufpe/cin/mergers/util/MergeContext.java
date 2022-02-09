package br.ufpe.cin.mergers.util;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import br.ufpe.cin.files.FilesManager;
import de.ovgu.cide.fstgen.ast.FSTNode;

/**
 * Encapsulates pertinent information of the merging process. A context
 * is also necessary to handle specific conflicts that simple
 * superimposition of trees is not able to address. 
 * @author Guilherme
 */
public class MergeContext {
	File base;
	File right;
	File left;
	String outputFilePath;
	public String fullyQualifiedMergedClass  = "";


	String baseContent = "";
	String leftContent = "";
	String rightContent= "";

	public List<FSTNode> addedLeftNodes = new ArrayList<FSTNode>();
	public List<FSTNode> addedRightNodes= new ArrayList<FSTNode>();

	public List<FSTNode> deletedBaseNodes = new ArrayList<FSTNode>();
	public List<FSTNode> nodesDeletedByLeft = new ArrayList<FSTNode>(); 
	public List<FSTNode> nodesDeletedByRight= new ArrayList<FSTNode>();

	public List<Pair<Side, FSTNode>> renamedWithoutBodyChanges = new ArrayList<>();
	public List<Pair<Side, FSTNode>> deletedOrRenamedWithBodyChanges = new ArrayList<>();
	
	public List<Pair<String,FSTNode>> possibleRenamedLeftNodes = new ArrayList<Pair<String,FSTNode>>();
	public List<Pair<String,FSTNode>> possibleRenamedRightNodes= new ArrayList<Pair<String,FSTNode>>();

	public List<FSTNode> editedLeftNodes = new ArrayList<FSTNode>(); 
	public List<FSTNode> editedRightNodes= new ArrayList<FSTNode>();


	public FSTNode leftTree;
	public FSTNode baseTree;
	public FSTNode rightTree;
	public FSTNode superImposedTree;
	public String semistructuredOutput;
	public String unstructuredOutput;

	//statistics
	public int newElementReferencingEditedOneConflicts = 0;
	public int typeAmbiguityErrorsConflicts = 0;
	public int deletionConflicts = 0;
	public int innerDeletionConflicts = 0;
	public int initializationBlocksConflicts = 0;
	public int acidentalConflicts = 0;
	public long semistructuredMergeTime = 0;
	public long unstructuredMergeTime 	= 0;
	public int semistructuredNumberOfConflicts = 0;
	public int unstructuredNumberOfConflicts   = 0;
	public int semistructuredMergeConflictsLOC = 0;
	public int unstructuredMergeConflictsLOC   = 0;
	public int orderingConflicts 			   = 0;
	public int duplicatedDeclarationErrors	   = 0;
	public int equalConflicts     = 0;
	public int renamingConflicts = 0;
	public Set<FSTNode> renamingVisitedMergeNodes = new HashSet<>();

	public MergeContext(){
	}

	public MergeContext(File left, File base, File right, String outputFilePath) {
		this.left = left;
		this.base = base;
		this.right= right;
		this.outputFilePath = outputFilePath;

		this.leftContent = FilesManager.readFileContent(this.left);
		this.baseContent = FilesManager.readFileContent(this.base);
		this.rightContent= FilesManager.readFileContent(this.right);

		this.setFullQualifiedMergedClassName();
	}

	/**
	 * Joins the information of another context.
	 * @param otherContext the context to be joined with
	 */
	public MergeContext join(MergeContext otherContext){
		this.addedLeftNodes. addAll(otherContext.addedLeftNodes);
		this.addedRightNodes.addAll(otherContext.addedRightNodes);

		this.editedLeftNodes. addAll(otherContext.editedLeftNodes);
		this.editedRightNodes.addAll(otherContext.editedRightNodes);

		this.deletedBaseNodes. addAll(otherContext.deletedBaseNodes);
		this.nodesDeletedByLeft. addAll(otherContext.nodesDeletedByLeft);
		this.nodesDeletedByRight. addAll(otherContext.nodesDeletedByRight);

		this.renamedWithoutBodyChanges.addAll(otherContext.renamedWithoutBodyChanges);
		this.deletedOrRenamedWithBodyChanges.addAll(otherContext.deletedOrRenamedWithBodyChanges);

		this.possibleRenamedLeftNodes. addAll(otherContext.possibleRenamedLeftNodes);
		this.possibleRenamedRightNodes.addAll(otherContext.possibleRenamedRightNodes);

		this.leftTree = otherContext.leftTree;
		this.baseTree = otherContext.baseTree;
		this.rightTree = otherContext.rightTree;
		this.superImposedTree = otherContext.superImposedTree;

		/*		this.renamingConflicts	+=	otherContext.renamingConflicts;
		this.newElementReferencingEditedOneConflicts	+=	otherContext.newElementReferencingEditedOneConflicts;
		this.typeAmbiguityErrorsConflicts	+=	otherContext.typeAmbiguityErrorsConflicts;
		this.deletionConflicts	+=	otherContext.deletionConflicts;
		this.initializationBlocksConflicts	+= otherContext.initializationBlocksConflicts;*/

		return this;
	}

	public File getBase() {
		return base;
	}

	public void setBase(File base) {
		this.base = base;
	}

	public File getRight() {
		return right;
	}

	public void setRight(File right) {
		this.right = right;
	}

	public File getLeft() {
		return left;
	}

	public void setLeft(File left) {
		this.left = left;
	}

	public String getBaseContent() {
		return baseContent;
	}

	public void setBaseContent(String baseContent) {
		this.baseContent = baseContent;
	}

	public String getLeftContent() {
		return leftContent;
	}

	public void setLeftContent(String leftContent) {
		this.leftContent = leftContent;
	}

	public String getRightContent() {
		return rightContent;
	}

	public void setRightContent(String rightContent) {
		this.rightContent = rightContent;
	}

	private void setFullQualifiedMergedClassName(){
		String name = "";
		if(this.base != null && this.base.length()!=0){
			name = FilesManager.getFullyQualifiedName(this.base);
		} else if(this.left != null && this.left.length()!=0 ){
			name = FilesManager.getFullyQualifiedName(this.left);
		} else if(this.right != null && this.right.length()!=0){
			name = FilesManager.getFullyQualifiedName(this.right);
		} 
		if(name.isEmpty()){
			name = (this.left 	!= null ? this.left.getAbsolutePath() : "<empty left>") + "#" + (
					this.base 	!= null ? this.base.getAbsolutePath() : "<empty base>") + "#" + (
					this.right 	!= null ? this.right.getAbsolutePath(): "<empty right>");
		}
		
		//more detailed info when executed mining repositories
		String projectAndCommit = "";
		File executionLog = new File(System.getProperty("user.home") + File.separator + ".jfstmerge" + File.separator + "execution.log");
		if(executionLog.exists()){
			projectAndCommit = FilesManager.lastLine(executionLog.getAbsolutePath())+",";
		}
		
		this.fullyQualifiedMergedClass = projectAndCommit+name;
	}
}
