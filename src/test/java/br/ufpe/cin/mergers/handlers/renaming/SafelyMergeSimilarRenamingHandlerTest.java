package br.ufpe.cin.mergers.handlers.renaming;

import java.io.File;
import java.io.UnsupportedEncodingException;

import org.junit.BeforeClass;
import org.junit.Test;

import br.ufpe.cin.app.JFSTMerge;
import br.ufpe.cin.mergers.util.MergeContext;
import br.ufpe.cin.mergers.util.RenamingStrategy;
import br.ufpe.cin.util.TestUtils;

public class SafelyMergeSimilarRenamingHandlerTest {
	private File baseFile = new File("testfiles/renaming/method/base_method/Test.java");
	private File bodyChangedBelowSignatureFile = new File("testfiles/renaming/method/body_changes_only_1/Test.java");
	private File bodyChangedAtEndFile = new File("testfiles/renaming/method/body_changes_only_2/Test.java");
	private File renamedMethodWithoutBodyChangesFile1 = new File("testfiles/renaming/method/renamed_method_without_body_changes_1/Test.java");
	private File renamedMethodWithoutBodyChangesFile2 = new File("testfiles/renaming/method/renamed_method_without_body_changes_2/Test.java");
	private File renamedMethodWithBodyChangesFile1 = new File("testfiles/renaming/method/renamed_method_with_body_changes_1/Test.java");
	private File renamedMethodWithBodyChangesFile2 = new File("testfiles/renaming/method/renamed_method_with_body_changes_2/Test.java");
	private File removedMethodFile = new File("testfiles/renaming/method/deleted_method/Test.java");
	private File abstractMethod1 = new File("testfiles/renaming/abstract_method/left/Test.java");
	private File abstractMethod2 = new File("testfiles/renaming/abstract_method/base/Test.java");
	private File abstractMethod3 = new File("testfiles/renaming/abstract_method/right/Test.java");
	private File renamedTwoMethodsWithBodyChanges1 = new File("testfiles/renamingstatistics/left/Test.java");
	private File renamedTwoMethodsWithBodyChanges2 = new File("testfiles/renamingstatistics/base/Test.java");
	private File renamedTwoMethodsWithBodyChanges3 = new File("testfiles/renamingstatistics/right/Test.java");

    private JFSTMerge jfstMerge = new JFSTMerge();
    private MergeContext mergeContext;

    @BeforeClass
    public static void setUpBeforeClass() throws UnsupportedEncodingException {
        TestUtils.hideSystemOutput();

        JFSTMerge.renamingStrategy = RenamingStrategy.SAFELY_MERGE_SIMILAR;
    }

    /**********************
	* SINGLE RENAMING TESTS
	***********************/
    @Test
    public void testHandle_whenLeftRenamesMethodWithoutBodyChanges_andRightChangesBodyBelowSignature_shouldReportConflict() {
        merge(renamedMethodWithoutBodyChangesFile1, bodyChangedBelowSignatureFile);
        TestUtils.verifyMergeResultWithRenamingConflict(mergeContext,
                "<<<<<<<MINEpublicvoidmethod1(intx,inty){inta;=======publicvoidmethod(intx,inty){inte;>>>>>>>YOURSintb;intc;}");
	}
	
	@Test
	public void testHandle_whenRightRenamesMethodWithoutBodyChanges_andLeftChangesBodyBelowSignature_shouldReportConflict() {
		merge(bodyChangedBelowSignatureFile, renamedMethodWithoutBodyChangesFile1);
		TestUtils.verifyMergeResultWithRenamingConflict(mergeContext,
				"<<<<<<<MINEpublicvoidmethod(intx,inty){inte;=======publicvoidmethod1(intx,inty){inta;>>>>>>>YOURSintb;intc;}");
	}

    @Test
    public void testHandle_whenLeftRenamesMethodWithoutBodyChanges_andRightChangesBodyAtEnd_shouldNotReportConflict() {
        merge(renamedMethodWithoutBodyChangesFile1, bodyChangedAtEndFile);
        TestUtils.verifyMergeResultWithoutRenamingConflict(mergeContext, "publicvoidmethod1(intx,inty){inta;intb;inte;}");
	}

	@Test
	public void testHandle_whenRightRenamesMethodWithoutBodyChanges_andLeftChangesBodyAtEnd_shouldNotReportConflict() {
		merge(bodyChangedAtEndFile, renamedMethodWithoutBodyChangesFile1);
		TestUtils.verifyMergeResultWithoutRenamingConflict(mergeContext,
				"publicvoidmethod1(intx,inty){inta;intb;inte;}");
	}

	@Test
	public void testHandle_whenLeftRenamesMethodWithBodyChanges_andRightChangesBodyBelowSignature_shouldReportConflict() {
		merge(renamedMethodWithBodyChangesFile1, bodyChangedBelowSignatureFile);
		TestUtils.verifyMergeResultWithRenamingConflict(mergeContext,
				"<<<<<<<MINEpublicvoidmethod1(intx,inty){intd;=======publicvoidmethod(intx,inty){inte;>>>>>>>YOURSintb;intc;}");
	}

	@Test
	public void testHandle_whenRightRenamesMethodWithBodyChanges_andLeftChangesBodyBelowSignature_shouldReportConflict() {
		merge(bodyChangedBelowSignatureFile, renamedMethodWithBodyChangesFile1);
		TestUtils.verifyMergeResultWithRenamingConflict(mergeContext,
				"<<<<<<<MINEpublicvoidmethod(intx,inty){inte;=======publicvoidmethod1(intx,inty){intd;>>>>>>>YOURSintb;intc;}");
	}

	@Test
	public void testHandle_whenLeftRenamesMethodWithBodyChanges_andRightChangesBodyAtEnd_shouldNotReportConflict() {
		merge(renamedMethodWithBodyChangesFile1, bodyChangedAtEndFile);
		TestUtils.verifyMergeResultWithoutRenamingConflict(mergeContext, "publicvoidmethod1(intx,inty){intd;intb;inte;}");
	}

	@Test
	public void testHandle_whenRightRenamesMethodWithBodyChanges_andLeftChangesBodyAtEnd_shouldNotReportConflict() {
		merge(bodyChangedAtEndFile, renamedMethodWithBodyChangesFile1);
		TestUtils.verifyMergeResultWithoutRenamingConflict(mergeContext, "publicvoidmethod1(intx,inty){intd;intb;inte;}");
	}

	@Test
	public void testHandle_whenLeftDeletesMethod_andRightChangesBody_shouldReportConflict() {
		merge(removedMethodFile, bodyChangedBelowSignatureFile);
		TestUtils.verifyMergeResultWithRenamingConflict(mergeContext, "<<<<<<<MINE=======publicvoidmethod(intx,inty){inte;intb;intc;}>>>>>>>YOURS");
	}

	@Test
	public void testHandle_whenRightDeletesMethod_andLeftChangesBody_shouldReportConflict() {
		merge(bodyChangedBelowSignatureFile, removedMethodFile);
		TestUtils.verifyMergeResultWithRenamingConflict(mergeContext, "<<<<<<<MINEpublicvoidmethod(intx,inty){inte;intb;intc;}=======>>>>>>>YOURS");
	}
	
	/**********************
	* MUTUAL RENAMING TESTS
	***********************/
    @Test
    public void testHandle_whenBothRenameMethodWithoutBodyChanges_andBothRenameToTheSameName_shouldNotReportConflict() {
		merge(renamedMethodWithoutBodyChangesFile1, renamedMethodWithoutBodyChangesFile1);
		TestUtils.verifyMergeResultWithoutRenamingConflict(mergeContext, "publicvoidmethod1(intx,inty){inta;intb;intc;}");
	}
	
	@Test
	public void testHandle_whenBothRenameMethodWithoutBodyChanges_andBothRenameToDifferentNames_shouldReportConflict() {
		merge(renamedMethodWithoutBodyChangesFile1, renamedMethodWithoutBodyChangesFile2);
		TestUtils.verifyMergeResultWithRenamingConflict(mergeContext, "<<<<<<<MINEpublicvoidmethod1(intx,inty){inta;intb;intc;}=======publicvoidmethod2(intx,inty){inta;intb;intc;}>>>>>>>YOURS");
	}

	@Test
	public void testHandle_whenLeftRenamesMethodWithoutBodyChanges_andRightRenamesMethodWithBodyChangesAtBeginning_andBothRenameToTheSameName_shouldReportConflict() {
		merge(renamedMethodWithoutBodyChangesFile1, renamedMethodWithBodyChangesFile1);
		TestUtils.verifyMergeResultWithRenamingConflict(mergeContext,
				"publicvoidmethod1(intx,inty){<<<<<<<MINEinta;=======intd;>>>>>>>YOURSintb;intc;}");
	}

	@Test
	public void testHandle_whenRightRenamesMethodWithoutBodyChanges_andLeftRenamesMethodWithBodyChangesAtEnd_andBothRenameToTheSameName_shouldNotReportConflict() {
		merge(renamedMethodWithBodyChangesFile2, renamedMethodWithoutBodyChangesFile2);
		TestUtils.verifyMergeResultWithoutRenamingConflict(mergeContext,
				"publicvoidmethod2(intx,inty){inta;intb;intd;}");
	}

	@Test
	public void testHandle_whenLeftRenamesMethodWithoutBodyChanges_andRightRenamesMethodWithBodyChanges_andBothRenameToDifferentNames_shouldReportConflict() {
		merge(renamedMethodWithoutBodyChangesFile1, renamedMethodWithBodyChangesFile2);
		TestUtils.verifyMergeResultWithRenamingConflict(mergeContext,
				"<<<<<<<MINEpublicvoidmethod1(intx,inty){inta;intb;intc;}=======publicvoidmethod2(intx,inty){inta;intb;intd;}>>>>>>>YOURS");
	}

	@Test
	public void testHandle_whenRightRenamesMethodWithoutBodyChanges_andLeftRenamesMethodWithBodyChanges_andBothRenameToDifferentNames_shouldReportConflict() {
		merge(renamedMethodWithBodyChangesFile1, renamedMethodWithoutBodyChangesFile2);
		TestUtils.verifyMergeResultWithRenamingConflict(mergeContext,
				"<<<<<<<MINEpublicvoidmethod1(intx,inty){intd;intb;intc;}=======publicvoidmethod2(intx,inty){inta;intb;intc;}>>>>>>>YOURS");
	}

	@Test
	public void testHandle_whenBothRenameMethodWithBodyChanges_andBothRenameToTheSameName_shouldNotReportConflict() {
		merge(renamedMethodWithBodyChangesFile2, renamedMethodWithBodyChangesFile2);
		TestUtils.verifyMergeResultWithoutRenamingConflict(mergeContext, "publicvoidmethod2(intx,inty){inta;intb;intd;}");
	}

	@Test
	public void testHandle_whenBothRenameMethodWithBodyChanges_andBothRenameToDifferentNames_shouldReportConflict() {
		merge(renamedMethodWithBodyChangesFile1, renamedMethodWithBodyChangesFile2);
		TestUtils.verifyMergeResultWithRenamingConflict(mergeContext,
				"<<<<<<<MINEpublicvoidmethod1(intx,inty){intd;intb;intc;}=======publicvoidmethod2(intx,inty){inta;intb;intd;}>>>>>>>YOURS");
	}

	@Test
	@edu.umd.cs.findbugs.annotations.SuppressFBWarnings("ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD")
	public void testHandle_givenShowMessageOptionIsEnabled_whenBothRenameMethodWithBodyChanges_andBothRenameToDifferentNames_shouldReportConflict_andDisplayTheConflictMessage() {
		JFSTMerge.showConflictMessages = true;
		merge(renamedMethodWithBodyChangesFile1, renamedMethodWithBodyChangesFile2);
		JFSTMerge.showConflictMessages = false;
		TestUtils.verifyMergeResultWithRenamingConflict(mergeContext,
				"<<<<<<<MINEpublicvoidmethod1(intx,inty){intd;intb;intc;}=======doublerenamingtodifferentsignaturespublicvoidmethod2(intx,inty){inta;intb;intd;}>>>>>>>YOURS");
	}

	@Test
	public void testHandle_whenLeftDeletesMethod_andRightRenamesMethod_shouldReportConflict() {
		merge(removedMethodFile, renamedMethodWithBodyChangesFile1);
		TestUtils.verifyMergeResultWithRenamingConflict(mergeContext,
				"<<<<<<<MINE=======publicvoidmethod1(intx,inty){intd;intb;intc;}>>>>>>>YOURS");
	}

	@Test
	public void testHandle_whenRightDeletesMethod_andLeftRenamesMethod_shouldReportConflict() {
		merge(renamedMethodWithoutBodyChangesFile2, removedMethodFile);
		TestUtils.verifyMergeResultWithRenamingConflict(mergeContext,
				"<<<<<<<MINEpublicvoidmethod2(intx,inty){inta;intb;intc;}=======>>>>>>>YOURS");
	}

	@Test
	public void testHandle_whenBothDeleteMethod_shouldNotReportConflict() {
		merge(removedMethodFile, removedMethodFile);
		TestUtils.verifyMergeResultWithoutRenamingConflict(mergeContext,
				"publicclassTest{}");
	}

	@Test
	public void testHandle_abstractMethods_whenBothRenameToDifferentName_shouldReportConflict() {
		merge(abstractMethod1, abstractMethod2, abstractMethod3);
		TestUtils.verifyMergeResultWithRenamingConflict(mergeContext,
			"<<<<<<<MINEpublicabstractvoidn1();=======publicabstractvoidn2();>>>>>>>YOURS");
	}

	@Test
	public void testHandle_whenBothRenameTwoMethodsWithBodyChanges_shouldCountOneRenamingConflict() {
		merge(
			renamedTwoMethodsWithBodyChanges1,
			renamedTwoMethodsWithBodyChanges2,
			renamedTwoMethodsWithBodyChanges3
		);

		String expectedOutput = TestUtils.getTestExpectedOutput("renamingstatistics");
		TestUtils.verifyMergeResultWithRenamingConflict(mergeContext, expectedOutput);
	}

    private void merge(File left, File right) {
        mergeContext = jfstMerge.mergeFiles(left, baseFile, right, null);
	}
	
	private void merge(File left, File base, File right) {
		mergeContext = jfstMerge.mergeFiles(left, base, right, null);
	}
}