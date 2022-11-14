package br.ufpe.cin.mergers.handlers;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import br.ufpe.cin.app.JFSTMerge;
import br.ufpe.cin.files.FilesManager;
import br.ufpe.cin.mergers.util.MergeContext;

public class InitializationBlocksHandlerMultipleBlocksTest {

    JFSTMerge merge = new JFSTMerge();

	@BeforeClass
	public static void setUpBeforeClass() throws UnsupportedEncodingException {
		//hidding sysout output
		@SuppressWarnings("unused")
		PrintStream originalStream = System.out;
		PrintStream hideStream    = new PrintStream(new OutputStream(){
			public void write(int b) {}
		},  true, Charset.defaultCharset().displayName());
		System.setOut(hideStream);
	}
	
	@edu.umd.cs.findbugs.annotations.SuppressFBWarnings("ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD")
    @Before
	public void enableNewApproachForInitializationHandler() {
		JFSTMerge.isInitializationBlocksHandlerEnabled = false;
		JFSTMerge.isInitializationBlocksHandlerMultipleBlocksEnabled = true;
	}

	@edu.umd.cs.findbugs.annotations.SuppressFBWarnings("ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD")
	@After
	public void disableNewApproachForInitializationHandler() {
		JFSTMerge.isInitializationBlocksHandlerEnabled = true;
		JFSTMerge.isInitializationBlocksHandlerMultipleBlocksEnabled = false;
	}
    
	@Test
	public void testInitializationBlocksBlockAddition() {
		MergeContext ctx = 	merge.mergeFiles(
				new File("testfiles/initlblocksmultipleblocks/nobaseandblockaddition/left/Test.java"), 
				new File("testfiles/initlblocksmultipleblocks/nobaseandblockaddition/base/Test.java"), 
				new File("testfiles/initlblocksmultipleblocks/nobaseandblockaddition/right/Test.java"), 
				null);
		
		String mergeResult = FilesManager.getStringContentIntoSingleLineNoSpacing(ctx.semistructuredOutput);

		assertThat(mergeResult).contains("publicclassTest{static{inta=3;}}");
		assertThat(ctx.initializationBlocksConflicts).isZero();
	}
	
	@Test
	public void testInitializationBlocksNoEditionAndBlockDeletion() {
		MergeContext ctx = 	merge.mergeFiles(
				new File("testfiles/initlblocksmultipleblocks/noeditionandblockdeletion/left/Test.java"), 
				new File("testfiles/initlblocksmultipleblocks/noeditionandblockdeletion/base/Test.java"), 
				new File("testfiles/initlblocksmultipleblocks/noeditionandblockdeletion/right/Test.java"), 
				null);
		
		String mergeResult = FilesManager.getStringContentIntoSingleLineNoSpacing(ctx.semistructuredOutput);

		assertThat(mergeResult).contains("publicclassTest{}");
		assertThat(ctx.initializationBlocksConflicts).isZero();
	}
	
	@Test
	public void testInitializationBlocksEditionAndBlockDeletion() {
		MergeContext ctx = 	merge.mergeFiles(
				new File("testfiles/initlblocksmultipleblocks/editionanddeletion/left/Test.java"), 
				new File("testfiles/initlblocksmultipleblocks/editionanddeletion/base/Test.java"), 
				new File("testfiles/initlblocksmultipleblocks/editionanddeletion/right/Test.java"), 
				null);
		
		String mergeResult = FilesManager.getStringContentIntoSingleLineNoSpacing(ctx.semistructuredOutput);

		assertThat(mergeResult).contains("publicclassTest{<<<<<<<MINEstatic{inta=4;}=======>>>>>>>YOURS}");
		assertThat(ctx.initializationBlocksConflicts).isOne();
	}
	
	@Test
	public void testInitializationBlocksDoubledBlockAddition() {
		MergeContext ctx = 	merge.mergeFiles(
				new File("testfiles/initlblocksmultipleblocks/doubledblockaddition/left/Test.java"), 
				new File("testfiles/initlblocksmultipleblocks/doubledblockaddition/base/Test.java"), 
				new File("testfiles/initlblocksmultipleblocks/doubledblockaddition/right/Test.java"), 
				null);
		 
		String mergeResult = FilesManager.getStringContentIntoSingleLineNoSpacing(ctx.semistructuredOutput);

		assertThat(mergeResult).contains("publicclassTest{static{inta=3;}}");
		assertThat(ctx.initializationBlocksConflicts).isZero();
	}
	
	@Test
	public void testInitializationBlocksAdditionOfSimilarBlocks() {
		MergeContext ctx = 	merge.mergeFiles(
				new File("testfiles/initlblocksmultipleblocks/additionofsimilarblocks/left/Test.java"), 
				new File("testfiles/initlblocksmultipleblocks/additionofsimilarblocks/base/Test.java"), 
				new File("testfiles/initlblocksmultipleblocks/additionofsimilarblocks/right/Test.java"), 
				null);
		
		String mergeResult = FilesManager.getStringContentIntoSingleLineNoSpacing(ctx.semistructuredOutput);

		assertThat(mergeResult).contains("publicclassTest{static{inta=3;}static{inta=5;}");
		assertThat(ctx.initializationBlocksConflicts).isZero();
	}
	
	@Test
	public void testInitializationBlocksAdditionOfSimilarBlocksDiffVars() {
		MergeContext ctx = 	merge.mergeFiles(
				new File("testfiles/initlblocksmultipleblocks/additionofsimilarblocksdiffvars/left/Test.java"), 
				new File("testfiles/initlblocksmultipleblocks/additionofsimilarblocksdiffvars/base/Test.java"), 
				new File("testfiles/initlblocksmultipleblocks/additionofsimilarblocksdiffvars/right/Test.java"), 
				null);
		
		String mergeResult = FilesManager.getStringContentIntoSingleLineNoSpacing(ctx.semistructuredOutput);

		assertThat(mergeResult).contains("publicclassTest{static{inta=3;}static{intb=5;}}");
		assertThat(ctx.initializationBlocksConflicts).isZero();
	}
	
	@Test
	public void testInitializationBlocksEditionInOnlyOneBlock() {
		MergeContext ctx = 	merge.mergeFiles(
				new File("testfiles/initlblocksmultipleblocks/editioninonlyoneblock/left/Test.java"), 
				new File("testfiles/initlblocksmultipleblocks/editioninonlyoneblock/base/Test.java"), 
				new File("testfiles/initlblocksmultipleblocks/editioninonlyoneblock/right/Test.java"), 
				null);
		
		String mergeResult = FilesManager.getStringContentIntoSingleLineNoSpacing(ctx.semistructuredOutput);

		assertThat(mergeResult).contains("publicclassTest{static{inta=4;}}");
		assertThat(ctx.initializationBlocksConflicts).isZero();
	}
	
	@Test
	public void testInitializationBlocksEditionInBothBranches() {
		MergeContext ctx = 	merge.mergeFiles(
				new File("testfiles/initlblocksmultipleblocks/editioninbothbranches/left/Test.java"), 
				new File("testfiles/initlblocksmultipleblocks/editioninbothbranches/base/Test.java"), 
				new File("testfiles/initlblocksmultipleblocks/editioninbothbranches/right/Test.java"), 
				null);
		
		String mergeResult = FilesManager.getStringContentIntoSingleLineNoSpacing(ctx.semistructuredOutput);

		assertThat(mergeResult).contains("publicclassTest{static{<<<<<<<MINEinta=4;=======inta=5;>>>>>>>YOURS}}");
		assertThat(ctx.initializationBlocksConflicts).isOne();
	}
	
	@Test
	public void testInitializationBlocksEditionAndBlockAddition() {
		MergeContext ctx = 	merge.mergeFiles(
				new File("testfiles/initlblocksmultipleblocks/editionandblockaddition/left/Test.java"), 
				new File("testfiles/initlblocksmultipleblocks/editionandblockaddition/base/Test.java"), 
				new File("testfiles/initlblocksmultipleblocks/editionandblockaddition/right/Test.java"), 
				null);
		
		String mergeResult = FilesManager.getStringContentIntoSingleLineNoSpacing(ctx.semistructuredOutput);

		assertThat(mergeResult).contains("publicclassTest{<<<<<<<MINEstatic{inta=4;}=======>>>>>>>YOURSstatic"
				+ "{intb=5;intc=5;intd=5;inte=5;}}");
		assertThat(ctx.initializationBlocksConflicts).isOne();
	}
	
	@Test
	public void testInitializationBlocksEditionAndContentInsertion() {
		MergeContext ctx = 	merge.mergeFiles(
				new File("testfiles/initlblocksmultipleblocks/editionandcontentinsertion/left/Test.java"), 
				new File("testfiles/initlblocksmultipleblocks/editionandcontentinsertion/base/Test.java"), 
				new File("testfiles/initlblocksmultipleblocks/editionandcontentinsertion/right/Test.java"), 
				null);
		
		String mergeResult = FilesManager.getStringContentIntoSingleLineNoSpacing(ctx.semistructuredOutput);

		assertThat(mergeResult).contains("publicclassTest{static{<<<<<<<MINEinta=4;=======inta=3;intc=5;intd=5;inte=5;"
				+ ">>>>>>>YOURS}}");
		assertThat(ctx.initializationBlocksConflicts).isOne();
	}
	
	@Test
	public void testInitializationBlocksEditionOrAdditionInBothBranchesIndependentVars() {
		MergeContext ctx = 	merge.mergeFiles(
				new File("testfiles/initlblocksmultipleblocks/editionoradditioninbothbranchesindependentvars/"
						+ "left/Test.java"), 
				new File("testfiles/initlblocksmultipleblocks/editionoradditioninbothbranchesindependentvars/"
						+ "base/Test.java"), 
				new File("testfiles/initlblocksmultipleblocks/editionoradditioninbothbranchesindependentvars/"
						+ "right/Test.java"), 
				null);
		
		String mergeResult = FilesManager.getStringContentIntoSingleLineNoSpacing(ctx.semistructuredOutput);
		
		assertThat(mergeResult).contains("publicclassTest{static{inth=4;intf=6;intg=6;}static{intb=5;intc=5;intd=5;"
				+ "inte=5;}}");
		assertThat(ctx.initializationBlocksConflicts).isZero();
	}
	
	@Test
	public void testInitializationBlocksMultipleBlocksEditionInOnlyOneBranch() {
		MergeContext ctx = 	merge.mergeFiles(
				new File("testfiles/initlblocksmultipleblocks/multipleblockseditioninonlyonebranch/left/Test.java"), 
				new File("testfiles/initlblocksmultipleblocks/multipleblockseditioninonlyonebranch/base/Test.java"), 
				new File("testfiles/initlblocksmultipleblocks/multipleblockseditioninonlyonebranch/right/Test.java"), 
				null);
		
		String mergeResult = FilesManager.getStringContentIntoSingleLineNoSpacing(ctx.semistructuredOutput);

		assertThat(mergeResult).contains("publicclassTest{static{inta=3;}static{inta=7;intb=8;}}");
		assertThat(ctx.initializationBlocksConflicts).isZero();
	}
	
	@Test
	public void testInitializationBlocksMultipleBlocksEditionInBothBranchesBlocks() {
		MergeContext ctx = 	merge.mergeFiles(
				new File("testfiles/initlblocksmultipleblocks/multipleblockseditioninbothbranchesblocks/left/Test.java"), 
				new File("testfiles/initlblocksmultipleblocks/multipleblockseditioninbothbranchesblocks/base/Test.java"), 
				new File("testfiles/initlblocksmultipleblocks/multipleblockseditioninbothbranchesblocks/right/Test.java"), 
				null);
		
		String mergeResult = FilesManager.getStringContentIntoSingleLineNoSpacing(ctx.semistructuredOutput);

		assertThat(mergeResult).contains("publicclassTest{static{<<<<<<<MINEintvar_a=1;intvar_b=2;intvar_c=3;======="
				+ "inta=1;intb=2;intc=3;for(inti=0;i<5;i++){System.out.println(\"Indice\"+i);}>>>>>>>YOURS}"
				+ "static{inta=7;intb=8;intc=9;}}");
		assertThat(ctx.initializationBlocksConflicts).isOne();
	}
	
	@Test
	public void testInitializationBlocksMultipleBlocksReordering() {
		MergeContext ctx = 	merge.mergeFiles(
				new File("testfiles/initlblocksmultipleblocks/multipleblocksreordering/left/Test.java"), 
				new File("testfiles/initlblocksmultipleblocks/multipleblocksreordering/base/Test.java"), 
				new File("testfiles/initlblocksmultipleblocks/multipleblocksreordering/right/Test.java"), 
				null);
		
		String mergeResult = FilesManager.getStringContentIntoSingleLineNoSpacing(ctx.semistructuredOutput);

		assertThat(mergeResult).contains("publicclassTest{static{inta=1;intb=2;intc=3;intd=4;}static{intd=4;inte=5;"
				+ "intf=6;}}");
		assertThat(ctx.initializationBlocksConflicts).isZero();
	}
	
	@Test
	public void testInitializationBlocksMultipleBlocksEditionAndDeletion() {
		MergeContext ctx = 	merge.mergeFiles(
				new File("testfiles/initlblocksmultipleblocks/multipleblockseditionanddeletion/left/Test.java"), 
				new File("testfiles/initlblocksmultipleblocks/multipleblockseditionanddeletion/base/Test.java"), 
				new File("testfiles/initlblocksmultipleblocks/multipleblockseditionanddeletion/right/Test.java"), 
				null);
		
		String mergeResult = FilesManager.getStringContentIntoSingleLineNoSpacing(ctx.semistructuredOutput);

		assertThat(mergeResult).contains("publicclassTest{static{inta=4;}}");
		assertThat(ctx.initializationBlocksConflicts).isZero();
	}
	
	@Test
	public void testInitializationBlocksMultipleBlocksEditionInBothBranchesAndDeletion() {
		MergeContext ctx = 	merge.mergeFiles(
				new File("testfiles/initlblocksmultipleblocks/multipleblockseditiondinbothbranchesanddeletion/"
						+ "left/Test.java"), 
				new File("testfiles/initlblocksmultipleblocks/multipleblockseditiondinbothbranchesanddeletion/"
						+ "base/Test.java"), 
				new File("testfiles/initlblocksmultipleblocks/multipleblockseditiondinbothbranchesanddeletion/"
						+ "right/Test.java"), 
				null);
		
		String mergeResult = FilesManager.getStringContentIntoSingleLineNoSpacing(ctx.semistructuredOutput);

		assertThat(mergeResult).contains("publicclassTest{static{<<<<<<<MINEinta=4;=======intvar_a=3;>>>>>>>YOURS}}");
		assertThat(ctx.initializationBlocksConflicts).isOne();
	}
	
	@Test
	public void testInitializationBlocksEditionOrAdditionInBothBranchesDependentVars() {
		MergeContext ctx = 	merge.mergeFiles(
				new File("testfiles/initlblocksmultipleblocks/editionoradditioninbothbranchesdependentvars/"
						+ "left/Test.java"), 
				new File("testfiles/initlblocksmultipleblocks/editionoradditioninbothbranchesdependentvars/"
						+ "base/Test.java"), 
				new File("testfiles/initlblocksmultipleblocks/editionoradditioninbothbranchesdependentvars/"
						+ "right/Test.java"), 
				null);
		
		String mergeResult = FilesManager.getStringContentIntoSingleLineNoSpacing(ctx.semistructuredOutput);

		assertThat(mergeResult).contains("publicclassTest{staticinth=4;static{<<<<<<<MINEh=5;intf=6;intg=6;"
				+ "=======intb=5;intc=5;intg=5;inte=h;>>>>>>>YOURS}}");
		assertThat(ctx.initializationBlocksConflicts).isOne();
	}
	
	@Test
	public void testInitializationBlocksAdditionInBothBranchesDependentVarsUsingSetter() {
		MergeContext ctx = 	merge.mergeFiles(
				new File("testfiles/initlblocksmultipleblocks/additioninbothbranchesdependentvarsusingsetter/"
						+ "left/Test.java"), 
				new File("testfiles/initlblocksmultipleblocks/additioninbothbranchesdependentvarsusingsetter/"
						+ "base/Test.java"), 
				new File("testfiles/initlblocksmultipleblocks/additioninbothbranchesdependentvarsusingsetter/"
						+ "right/Test.java"), 
				null);
		
		String mergeResult = FilesManager.getStringContentIntoSingleLineNoSpacing(ctx.semistructuredOutput);

		assertThat(mergeResult).contains("publicclassTestextendsSuperTest{static{<<<<<<<MINEh=5;intf=6;intg=6;"
				+ "=======intb=5;intc=5;intg=5;h.setValue(8);>>>>>>>YOURS}}");
		assertThat(ctx.initializationBlocksConflicts).isOne();	
	}
	
	@Test
	public void testInitializationBlocksEditionOrAdditionInBothBranchesDependentVarsWithConditional() {		
		MergeContext ctx = 	merge.mergeFiles(
				new File("testfiles/initlblocksmultipleblocks/editionoradditioninbothbranchesdependentvarswithconditional/"
						+ "left/Test.java"), 
				new File("testfiles/initlblocksmultipleblocks/editionoradditioninbothbranchesdependentvarswithconditional/"
						+ "base/Test.java"), 
				new File("testfiles/initlblocksmultipleblocks/editionoradditioninbothbranchesdependentvarswithconditional/"
						+ "right/Test.java"), 
				null);
		
		String mergeResult = FilesManager.getStringContentIntoSingleLineNoSpacing(ctx.semistructuredOutput);

		assertThat(mergeResult).contains("publicclassTest{staticinth=4;static{<<<<<<<MINEif(h==4){h=5;}intf=6;intg=6;"
				+ "=======intb=5;intc=5;intg=5;if(h==4){inte=h;}>>>>>>>YOURS}}");
		assertThat(ctx.initializationBlocksConflicts).isOne();
	}
}
