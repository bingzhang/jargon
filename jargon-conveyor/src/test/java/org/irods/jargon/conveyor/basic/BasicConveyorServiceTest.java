package org.irods.jargon.conveyor.basic;

import junit.framework.Assert;

import org.irods.jargon.conveyor.core.ConveyorService;
import org.irods.jargon.testutils.TestingPropertiesHelper;
import org.irods.jargon.transfer.exception.PassPhraseInvalidException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.transaction.TransactionConfiguration;
import org.springframework.transaction.annotation.Transactional;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath:transfer-dao-beans.xml",
		"classpath:transfer-dao-hibernate-spring.cfg.xml" })
@TransactionConfiguration(transactionManager = "transactionManager", defaultRollback = true)
@Transactional
public class BasicConveyorServiceTest {

	public static final String testPassPhrase = "BasicConveyorServiceTest";
	@Autowired
	private ConveyorService conveyorService;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		org.irods.jargon.testutils.TestingPropertiesHelper testingPropertiesLoader = new TestingPropertiesHelper();
		testingPropertiesLoader.getTestProperties();
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Test
	public void testInitializeAndValidatePassPhrase() throws Exception {
		conveyorService.resetConveyorService();
		conveyorService.validatePassPhrase(testPassPhrase);
		Assert.assertTrue("did not validate pass phrase",
				conveyorService.getGridAccountService().getCachedPassPhrase()
						.equals(testPassPhrase));
	}

	@Test
	public void testIsPassPhraseValidatedWhenNotValidated() throws Exception {
		conveyorService.resetConveyorService();
		boolean actual = conveyorService.isPreviousPassPhraseStored();
		Assert.assertFalse("should not show pass phrase as validated", actual);

	}

	@Test
	public void testIsPassPhraseValidatedWhenValidated() throws Exception {
		conveyorService.resetConveyorService();
		conveyorService.validatePassPhrase(testPassPhrase);
		boolean actual = conveyorService.isPreviousPassPhraseStored();
		Assert.assertTrue("should not show pass phrase as validated", actual);

	}

	@Test(expected = PassPhraseInvalidException.class)
	public void testUseInvalidPassPhrase() throws Exception {
		conveyorService.resetConveyorService();
		conveyorService.validatePassPhrase(testPassPhrase);
		conveyorService.validatePassPhrase("iaminvalidhere");

	}

	@Test
	public void testResetAndThenCheckNotValid() throws Exception {
		conveyorService.resetConveyorService();
		conveyorService.validatePassPhrase(testPassPhrase);
		Assert.assertTrue("did not validate pass phrase",
				conveyorService.getGridAccountService().getCachedPassPhrase()
						.equals(testPassPhrase));
		conveyorService.resetConveyorService();
		Assert.assertTrue("should not be validated", conveyorService
				.getGridAccountService().getCachedPassPhrase().isEmpty());
	}

}
