package org.irods.jargon.core.pub;

import java.io.File;

import org.irods.jargon.core.connection.IRODSAccount;
import org.irods.jargon.core.connection.IRODSSession;
import org.irods.jargon.core.exception.DuplicateDataException;
import org.irods.jargon.core.exception.JargonException;
import org.irods.jargon.core.exception.JargonFileOrCollAlreadyExistsException;
import org.irods.jargon.core.packinstr.DataObjCopyInp;
import org.irods.jargon.core.pub.io.IRODSFile;
import org.irods.jargon.core.pub.io.IRODSFileFactory;
import org.irods.jargon.core.transfer.DefaultTransferControlBlock;
import org.irods.jargon.core.transfer.TransferControlBlock;
import org.irods.jargon.core.transfer.TransferStatus;
import org.irods.jargon.core.transfer.TransferStatus.TransferState;
import org.irods.jargon.core.transfer.TransferStatus.TransferType;
import org.irods.jargon.core.transfer.TransferStatusCallbackListener;
import org.irods.jargon.core.utils.LocalFileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This object centralizes data transfer operations for iRODS, and provides
 * methods to move data to and from iRODS, as well as transfers between iRODS
 * resources. Some of the methods in this object are implemented elsewhere, and
 * are delegated to in order to provide one class for typical data movement
 * operations.
 * <p/>
 * Note that this object treats data objects and collections as objects, instead
 * of emulating <code>File</code> operations. There is a package that implements
 * iRODS data objects and collections as typical <code>java.io.*</code> objects
 * that can be found in the <code>org.irods.jargon.core.pub.io.*</code> package.
 * <p/>
 * Note that there are objects that can be used to access and manipulate data
 * and metadata about data objects and collections, and to query about data
 * objects and collections. These can be found in the
 * {@link org.irods.jargon.core.pub.DataObjectAO} and
 * {@link org.irods.jargon.core.pub.CollectionAOImpl}. Those access objects can
 * retrieve domain objects that represent details about collections and data
 * objects.
 * 
 * @author Mike Conway - DICE (www.irods.org)
 * 
 */
public final class DataTransferOperationsImpl extends IRODSGenericAO implements
		DataTransferOperations {

	private static Logger log = LoggerFactory
			.getLogger(DataTransferOperationsImpl.class);
	private TransferOperationsHelper transferOperationsHelper = null;

	/**
	 * @param irodsSession
	 * @param irodsAccount
	 * @throws JargonException
	 */
	protected DataTransferOperationsImpl(final IRODSSession irodsSession,
			final IRODSAccount irodsAccount) throws JargonException {
		super(irodsSession, irodsAccount);
		this.transferOperationsHelper = TransferOperationsHelper.instance(
				irodsSession, irodsAccount);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.irods.jargon.core.pub.DataTransferOperations#physicalMove(java.lang
	 * .String, java.lang.String)
	 */
	@Override
	public void physicalMove(final String absolutePathToSourceFile,
			final String targetResource) throws JargonException {
		// just delegate to the phymove implementation. Method contracts are
		// checked there.
		final IRODSFileSystemAO irodsFileSystemAO = this
				.getIRODSAccessObjectFactory().getIRODSFileSystemAO(
						getIRODSAccount());
		irodsFileSystemAO
				.physicalMove(absolutePathToSourceFile, targetResource);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.DataTransferOperations#
	 * moveTheSourceCollectionUnderneathTheTargetCollectionUsingSourceParentCollectionName
	 * (java.lang.String, java.lang.String)
	 */
	@Override
	public void moveTheSourceCollectionUnderneathTheTargetCollectionUsingSourceParentCollectionName(
			final String absolutePathToSourceFile,
			final String absolutePathToTheTargetCollection)
			throws JargonFileOrCollAlreadyExistsException, JargonException {

		if (absolutePathToSourceFile == null
				|| absolutePathToSourceFile.isEmpty()) {
			throw new JargonException("null absolutePathToSourceFile");
		}

		if (absolutePathToTheTargetCollection == null
				|| absolutePathToTheTargetCollection.isEmpty()) {
			throw new JargonException(
					"absolutePathToTheTargetCollection is empty");
		}

		log.info("processing a move from {}", absolutePathToSourceFile);
		log.info("to {}", absolutePathToTheTargetCollection);

		final IRODSFileFactory irodsFileFactory = this.getIRODSFileFactory();

		final IRODSFile sourceFile = irodsFileFactory
				.instanceIRODSFile(absolutePathToSourceFile);

		final IRODSFile targetParentFile = irodsFileFactory
				.instanceIRODSFile(absolutePathToTheTargetCollection);

		// source file must exist or error
		if (!sourceFile.exists()) {
			log.info("the source file does not exist, cannot move");
			throw new JargonException("source file does not exist");
		}

		if (!sourceFile.isDirectory()) {
			String msg = "source file is not a directory, cannot move under target";
			log.error(msg);
			throw new JargonException(msg);
		}

		if (!targetParentFile.isDirectory()) {
			String msg = "target file is not a directory, cannot move under target";
			log.error(msg);
			throw new JargonException(msg);
		}

		String lastPartOfSourcePath = sourceFile.getName();
		log.debug(
				"last part of source path to move under target collection is: {}",
				lastPartOfSourcePath);
		StringBuilder sb = new StringBuilder();
		sb.append(targetParentFile.getAbsolutePath());
		sb.append('/');
		sb.append(lastPartOfSourcePath);
		String collectionUnderTargetAbsPath = sb.toString();

		if (sourceFile.getAbsolutePath().equals(collectionUnderTargetAbsPath)) {
			log.warn("attempted move of directory {} to self silently ignored",
					sourceFile.getAbsolutePath());
			return;
		}

		// after all of the checks, build the packing instruction and send it to
		// iRODS

		DataObjCopyInp dataObjCopyInp = null;

		dataObjCopyInp = DataObjCopyInp.instanceForRenameCollection(
				absolutePathToSourceFile, sb.toString());

		try {
			getIRODSProtocol().irodsFunction(dataObjCopyInp);
		} catch (JargonException je) {
			log.error("jargon exception in move operation", je);
			throw je;
		}

		log.info("successful move");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.irods.jargon.core.pub.DataTransferOperations#move(java.lang.String,
	 * java.lang.String)
	 */
	@Override
	public void move(final String absolutePathToSourceFile,
			final String absolutePathToTargetFile)
			throws JargonFileOrCollAlreadyExistsException, JargonException {

		if (absolutePathToSourceFile == null
				|| absolutePathToSourceFile.isEmpty()) {
			throw new JargonException("null absolutePathToSourceFile");
		}

		if (absolutePathToTargetFile == null
				|| absolutePathToTargetFile.isEmpty()) {
			throw new JargonException("absolutePathToTargetFile is empty");
		}

		log.info("processing a move from {}", absolutePathToSourceFile);
		log.info("to {}", absolutePathToTargetFile);

		IRODSFile irodsSourceFile = this.getIRODSFileFactory()
				.instanceIRODSFile(absolutePathToSourceFile);
		IRODSFile irodsTargetFile = this.getIRODSFileFactory()
				.instanceIRODSFile(absolutePathToTargetFile);
		move(irodsSourceFile, irodsTargetFile);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.irods.jargon.core.pub.DataTransferOperations#move(org.irods.jargon
	 * .core.pub.io.IRODSFile, org.irods.jargon.core.pub.io.IRODSFile)
	 */
	@Override
	public void move(final IRODSFile irodsSourceFile,
			final IRODSFile irodsTargetFile)
			throws JargonFileOrCollAlreadyExistsException, JargonException {

		if (irodsSourceFile == null) {
			throw new IllegalArgumentException("null irodsSourceFile");
		}

		if (irodsTargetFile == null) {
			throw new IllegalArgumentException("null irodsTargetFile");
		}
		log.info("processing a move from {}", irodsSourceFile);
		log.info("to {}", irodsTargetFile);
		// source file must exist or error

		if (!irodsSourceFile.exists()) {
			log.info("the source file does not exist, cannot move");
			throw new JargonException("source file does not exist");
		}

		log.info("source file exists, is collection? : {}",
				irodsSourceFile.isDirectory());

		log.info("target file:{}", irodsTargetFile.getAbsolutePath());
		log.info("target file isDir? {}", irodsTargetFile.isDirectory());

		IRODSFile actualTargetFile = irodsTargetFile;

		if (irodsSourceFile.isFile() && irodsTargetFile.isDirectory()) {
			log.info("target file is a directory, automatically propogate the source file name to the target");
			actualTargetFile = this.getIRODSFileFactory().instanceIRODSFile(
					irodsTargetFile.getAbsolutePath(),
					irodsSourceFile.getName());
		}

		if (irodsSourceFile.getAbsolutePath().equals(
				actualTargetFile.getAbsolutePath())) {
			log.warn("attempt to move a fie: {} to the same file name, logged and ignored");
			return;
		}

		// build correct packing instruction for copy. The packing instructions
		// are different for files and collections.

		DataObjCopyInp dataObjCopyInp = null;

		if (irodsSourceFile.isFile()) {
			log.info("transfer is for a file");
			dataObjCopyInp = DataObjCopyInp.instanceForRenameFile(
					irodsSourceFile.getAbsolutePath(),
					actualTargetFile.getAbsolutePath());
		} else {
			log.info("transfer is for a collection");
			dataObjCopyInp = DataObjCopyInp.instanceForRenameCollection(
					irodsSourceFile.getAbsolutePath(),
					actualTargetFile.getAbsolutePath());
		}

		try {
			getIRODSProtocol().irodsFunction(dataObjCopyInp);
		} catch (JargonException je) {
			log.error("jargon exception in move operation", je);
			throw je;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.irods.jargon.core.pub.DataTransferOperations#getOperation(org.irods
	 * .jargon.core.pub.io.IRODSFile, java.io.File,
	 * org.irods.jargon.core.transfer.TransferStatusCallbackListener,
	 * org.irods.jargon.core.transfer.TransferControlBlock)
	 */
	@Override
	public void getOperation(
			final IRODSFile irodsSourceFile,
			final File targetLocalFile,
			final TransferStatusCallbackListener transferStatusCallbackListener,
			final TransferControlBlock transferControlBlock)
			throws JargonException {

		TransferControlBlock operativeTransferControlBlock = buildTransferControlBlockAndOptionsBasedOnParameters(
				transferControlBlock);
		
		log.info("getOperation()");
		
		if (transferStatusCallbackListener == null) {
			log.info("no transferStatusCallbackListener set for getOperation()");
		}
		
		try {

			if (irodsSourceFile == null) {
				throw new IllegalArgumentException("irods source file is null");
			}

			if (targetLocalFile == null) {
				throw new IllegalArgumentException("target local file is null");
			}
			
			
			

			log.info("get operation, irods source file is: {}",
					irodsSourceFile.getAbsolutePath());
			log.info("  local file for get: {}",
					targetLocalFile.getAbsolutePath());
			

			// FIXME: look at normalization here (file to dir, file to file, etc)

			File targetLocalFileNameForCallbacks = new File(
					targetLocalFile.getAbsolutePath(),
					irodsSourceFile.getName());
			log.info("file name normalized:{}", targetLocalFileNameForCallbacks);

			/*
			 * Compute the count of files to be transferred. This is different
			 * depending on whether this is a single file, or whether it's a
			 * collection.
			 */
			if (irodsSourceFile.isDirectory()) {
				log.debug("get operation, treating as a directory");
				if (operativeTransferControlBlock != null) {
					IRODSAccessObjectFactory irodsAccessObjectFactory = IRODSAccessObjectFactoryImpl
							.instance(getIRODSSession());
					CollectionAO collectionAO = irodsAccessObjectFactory
							.getCollectionAO(getIRODSAccount());
					int fileCount = collectionAO
							.countAllFilesUnderneathTheGivenCollection(irodsSourceFile
									.getAbsolutePath());
					log.info("get will transfer {} files)", fileCount);
					operativeTransferControlBlock
							.setTotalFilesToTransfer(fileCount);
				}

				// send a 0th file status callback that indicates initiation
				if (transferStatusCallbackListener != null) {
					TransferStatus status = TransferStatus.instance(
							TransferType.GET,
							irodsSourceFile.getAbsolutePath(),
							targetLocalFileNameForCallbacks.getAbsolutePath(),
							"", operativeTransferControlBlock
									.getTotalBytesToTransfer(),
							operativeTransferControlBlock
									.getTotalBytesTransferredSoFar(),
							operativeTransferControlBlock
									.getTotalFilesTransferredSoFar(),
							operativeTransferControlBlock
									.getTotalFilesToTransfer(),
							TransferState.OVERALL_INITIATION,
							this.getIRODSAccount().getHost(),
							this.getIRODSAccount().getZone());

					transferStatusCallbackListener
							.overallStatusCallback(status);
				}

				getOperationWhenSourceFileIsDirectory(irodsSourceFile,
						targetLocalFile, transferStatusCallbackListener,
						operativeTransferControlBlock);

				// send a status callback that indicates completion
				if (transferStatusCallbackListener != null) {
					TransferStatus status = TransferStatus.instance(
							TransferType.GET,
							irodsSourceFile.getAbsolutePath(),
							targetLocalFileNameForCallbacks.getAbsolutePath(),
							"", operativeTransferControlBlock
									.getTotalBytesToTransfer(),
							operativeTransferControlBlock
									.getTotalBytesTransferredSoFar(),
							operativeTransferControlBlock
									.getTotalFilesTransferredSoFar(),
							operativeTransferControlBlock
									.getTotalFilesToTransfer(),
							TransferState.OVERALL_COMPLETION,this.getIRODSAccount().getHost(),
							this.getIRODSAccount().getZone());

					transferStatusCallbackListener
							.overallStatusCallback(status);
				}
			} else {

				if (operativeTransferControlBlock != null) {
					operativeTransferControlBlock.setTotalFilesToTransfer(1);
				}

				// send a 0th file status callback that indicates initiation
				if (transferStatusCallbackListener != null) {
					TransferStatus status = TransferStatus.instance(
							TransferType.GET,
							irodsSourceFile.getAbsolutePath(), targetLocalFile
									.getAbsolutePath(), "", 0, 0, 0,
							operativeTransferControlBlock
									.getTotalFilesToTransfer(),
							TransferState.OVERALL_INITIATION,this.getIRODSAccount().getHost(),
							this.getIRODSAccount().getZone());

					transferStatusCallbackListener
							.overallStatusCallback(status);
				}

				processGetOfSingleFile(irodsSourceFile, targetLocalFile,
						transferStatusCallbackListener,
						operativeTransferControlBlock);

				// send a status callback that indicates completion
				if (transferStatusCallbackListener != null) {
					TransferStatus status = TransferStatus.instance(
							TransferType.GET,
							irodsSourceFile.getAbsolutePath(), targetLocalFile
									.getAbsolutePath(), "", 0, 0, 0,
							operativeTransferControlBlock
									.getTotalFilesToTransfer(),
							TransferState.OVERALL_COMPLETION,this.getIRODSAccount().getHost(),
							this.getIRODSAccount().getZone());

					transferStatusCallbackListener
							.overallStatusCallback(status);
				}

			}
		} catch (JargonException je) {
			log.warn(
					"unexpected error in transfer that should have been caught in the actual transfer handling code",
					je);
			processExceptionDuringGetOperation(irodsSourceFile,
					targetLocalFile, transferStatusCallbackListener,
					operativeTransferControlBlock, je);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.irods.jargon.core.pub.DataTransferOperations#getOperation(java.lang
	 * .String, java.lang.String, java.lang.String,
	 * org.irods.jargon.core.transfer.TransferStatusCallbackListener,
	 * org.irods.jargon.core.transfer.TransferControlBlock)
	 */
	@Override
	public void getOperation(
			final String irodsSourceFileAbsolutePath,
			final String targetLocalFileAbsolutePath,
			final String sourceResourceName,
			final TransferStatusCallbackListener transferStatusCallbackListener,
			final TransferControlBlock transferControlBlock)
			throws JargonException {

		if (irodsSourceFileAbsolutePath == null
				|| irodsSourceFileAbsolutePath.isEmpty()) {
			throw new IllegalArgumentException(
					"irodsSourceFileAbsolutePath is null or empty");
		}

		if (targetLocalFileAbsolutePath == null
				|| targetLocalFileAbsolutePath.isEmpty()) {
			throw new IllegalArgumentException(
					"targetLocalFileAbsolutePath is null or empty");
		}

		if (sourceResourceName == null) {
			throw new IllegalArgumentException("sourceResourceName is null");
		}

		log.info("get operation, irods source file is: {}",
				irodsSourceFileAbsolutePath);
		log.info("  local file for get: {}", targetLocalFileAbsolutePath);
		log.info("   specifiying resource:", sourceResourceName);

		File localFile = new File(targetLocalFileAbsolutePath);
		IRODSFile irodsSourceFile = getIRODSFileFactory().instanceIRODSFile(
				irodsSourceFileAbsolutePath);
		irodsSourceFile.setResource(sourceResourceName);
		getOperation(irodsSourceFile, localFile,
				transferStatusCallbackListener, transferControlBlock);
	}

	/**
	 * An exception has occurred during a get operation. This method will check
	 * to see if there is a callback listener. If there is, the exception is
	 * reported to the listener and quashed. If there is not a callback
	 * listener, the error is rethrown from this method.
	 * 
	 * @param irodsSourceFile
	 * @param targetLocalFile
	 * @param transferStatusCallbackListener
	 * @param transferControlBlock
	 * @param je
	 * @throws JargonException
	 */
	private void processExceptionDuringGetOperation(
			final IRODSFile irodsSourceFile,
			final File targetLocalFile,
			final TransferStatusCallbackListener transferStatusCallbackListener,
			final TransferControlBlock transferControlBlock,
			final JargonException je) throws JargonException {
		log.error("exception in transfer", je);

		int totalFiles = 0;
		int totalFilesSoFar = 0;

		if (transferControlBlock != null) {
			transferControlBlock.reportErrorInTransfer();
			totalFiles = transferControlBlock.getTotalFilesToTransfer();
			totalFilesSoFar = transferControlBlock
					.getTotalFilesTransferredSoFar();
		}

		if (transferStatusCallbackListener != null) {
			log.warn("exception will be passed back to existing callback listener");

			TransferStatus status = TransferStatus.instanceForException(
					TransferType.GET, irodsSourceFile.getAbsolutePath(),
					targetLocalFile.getAbsolutePath(), "",
					targetLocalFile.length(), targetLocalFile.length(),
					totalFilesSoFar, totalFiles, je,this.getIRODSAccount().getHost(),
					this.getIRODSAccount().getZone());

			transferStatusCallbackListener.statusCallback(status);

		} else {
			log.warn("exception will be re-thrown, as there is no status callback listener");
			throw je;

		}
	}

	/**
	 * Initiate a recursive get operation. The iRODS source file is a
	 * collection, and will be recursively processed.
	 * 
	 * @param irodsSourceFile
	 *            {@link org.irods.jargon.core.pub.io.IRODSFile} that is the
	 *            source of the get.
	 * @param targetLocalFile
	 *            <code>File</code> on the local file system to which the files
	 *            will be transferrred.
	 * @param transferStatusCallbackListener
	 *            {@link org.irods.jargon.core.transfer.TransferStatusCallbackListener}
	 *            implementation that will receive callbacks of success/failure
	 *            of each individual file transfer. This may be set to
	 *            <code>null</code>, in which case, exceptions that are thrown
	 *            will be rethrown by this method to the caller.
	 * @param transferControlBlock
	 *            {@link org.irods.jargon.core.transfer.TransferControlBlock}
	 *            implementation that is the communications mechanism between
	 *            the initiator of the transfer and the transfer process. This is required
	 * @throws JargonException
	 */
	private void getOperationWhenSourceFileIsDirectory(
			final IRODSFile irodsSourceFile,
			final File targetLocalFile,
			final TransferStatusCallbackListener transferStatusCallbackListener,
			final TransferControlBlock transferControlBlock)
			throws JargonException {

		log.info("getOperationWhenSourceFileIsDirectory");
			
		log.info("this get operation is recursive");
		if (targetLocalFile.exists() && !targetLocalFile.isDirectory()) {
			String msg = "attempt to put a collection (recursively) to a target local file that is not a directory";
			log.error(msg);
			throw new JargonException(msg);
		}

		String thisDirName = irodsSourceFile.getName();
		log.info(
				"this dir name, will be the new parent directory in the local file system:{}",
				thisDirName);

		File newParentDirectory = new File(targetLocalFile.getAbsolutePath(),
				thisDirName);

		boolean result = newParentDirectory.mkdir();
		if (!result) {
			log.warn("mkdirs for {} did not return success",
					newParentDirectory.getAbsolutePath());
		}

		log.debug("new parent directory created locally:{}",
				newParentDirectory.getAbsolutePath());

		transferOperationsHelper.recursivelyGet(irodsSourceFile,
				newParentDirectory, transferStatusCallbackListener,
				transferControlBlock);
	}

	/**
	 * In a transfer operation, process the given iRODS file as a data object to
	 * be retrieved.
	 * 
	 * @param irodsSourceFile
	 *            {@link org.irods.jargon.core.pub.io.IRODSFile} that is the
	 *            source of the get.
	 * @param targetLocalFile
	 *            <code>File</code> on the local file system to which the files
	 *            will be transferrred.
	 * @param transferStatusCallbackListener
	 *            {@link org.irods.jargon.core.transfer.TransferStatusCallbackListener}
	 *            implementation that will receive callbacks of success/failure
	 *            of each individual file transfer. This may be set to
	 *            <code>null</code>, in which case, exceptions that are thrown
	 *            will be rethrown by this method to the caller.
	 * @param transferControlBlock
	 *            {@link org.irods.jargon.core.transfer.TransferControlBlock}
	 *            implementation that is the communications mechanism between
	 *            the initiator of the transfer and the transfer process. 
	 * @throws JargonException
	 */
	private void processGetOfSingleFile(
			final IRODSFile irodsSourceFile,
			final File targetLocalFile,
			final TransferStatusCallbackListener transferStatusCallbackListener,
			final TransferControlBlock transferControlBlock)
			throws JargonException {
		log.info("get of single file");

		transferOperationsHelper.processGetOfSingleFile(irodsSourceFile,
				targetLocalFile, transferStatusCallbackListener,
				transferControlBlock);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.irods.jargon.core.pub.DataTransferOperations#putOperation(java.io
	 * .File, org.irods.jargon.core.pub.io.IRODSFile,
	 * org.irods.jargon.core.transfer.TransferStatusCallbackListener,
	 * org.irods.jargon.core.transfer.TransferControlBlock)
	 */
	@Override
	public void putOperation(
			final File sourceFile,
			final IRODSFile targetIrodsFile,
			final TransferStatusCallbackListener transferStatusCallbackListener,
			final TransferControlBlock transferControlBlock)
			throws JargonException {

		

		TransferControlBlock operativeTransferControlBlock = buildTransferControlBlockAndOptionsBasedOnParameters(
				transferControlBlock);
		
		try {

			if (targetIrodsFile == null) {
				throw new JargonException("targetIrodsFile is null");
			}

			if (sourceFile == null) {
				throw new JargonException("sourceFile is null");
			}

			// the transfer status callback listener can be null

			log.info("put operation for source: {}",
					sourceFile.getAbsolutePath());
			log.info(" to target: {}", targetIrodsFile.getAbsolutePath());

			if (targetIrodsFile.getResource().isEmpty()) {
				log.debug("no resource provided, substitute the resource from the irodsAccount");
				targetIrodsFile.setResource(getIRODSAccount()
						.getDefaultStorageResource());
			}

			log.info("  resource:{}", targetIrodsFile.getResource());

			/*
			 * If a callback listener is given, make sure there is a transfer
			 * control block, and do a pre-scan of the iRODS collection to get a
			 * count of files to be transferred
			 */
			
			// look for recursive put (directory to collection) and process,
			// otherwise, just put the file

			if (sourceFile.isDirectory()) {

				preCountLocalFilesBeforeTransfer(sourceFile,
						operativeTransferControlBlock);
				
				putWhenSourceFileIsDirectory(sourceFile, targetIrodsFile,
						transferStatusCallbackListener,
						operativeTransferControlBlock);


			} else {
				
					operativeTransferControlBlock.setTotalFilesToTransfer(1);
				
				/* source file is a file, target is either a collection, or specifies the file. 
				 * If the target exists, or the target parent exists, format the appropriate call-back so that
				 * it depicts the resulting file 
				 */
				
				StringBuilder targetIrodsPathBuilder = new StringBuilder();
				
				/*
				 * Reset the iRODS file, as the directory may have been created prior to the put operation.  The reset clears the cache
				 * of the exists(), isFile(), and other basic file stat info
				 * 
				 */
				targetIrodsFile.reset();
				if (targetIrodsFile.exists() && targetIrodsFile.isDirectory()) {
					log.info("target is a directory, source is file");
					targetIrodsPathBuilder.append(targetIrodsFile.getAbsolutePath());
					targetIrodsPathBuilder.append("/");
					targetIrodsPathBuilder.append(sourceFile.getName());
				} else if (targetIrodsFile.getParentFile().exists() && targetIrodsFile.getParentFile().isDirectory()) {
					log.info("treating target as a file, using the whole path");
					targetIrodsPathBuilder.append(targetIrodsFile.getAbsolutePath());
				}
				
				String callbackTargetIrodsPath = targetIrodsPathBuilder.toString();
				log.info("computed callbackTargetIrodsPath:{}", callbackTargetIrodsPath);

				// send 0th file status callback that indicates startup
				if (transferStatusCallbackListener != null) {
					TransferStatus status = TransferStatus.instance(
							TransferType.PUT, sourceFile.getAbsolutePath(),
							callbackTargetIrodsPath, "",
							operativeTransferControlBlock
									.getTotalBytesToTransfer(),
							operativeTransferControlBlock
									.getTotalBytesTransferredSoFar(),
							operativeTransferControlBlock
									.getTotalFilesTransferredSoFar(),
							operativeTransferControlBlock
									.getTotalFilesToTransfer(),
							TransferState.OVERALL_INITIATION,this.getIRODSAccount().getHost(),
							this.getIRODSAccount().getZone());
					transferStatusCallbackListener
							.overallStatusCallback(status);
				}

				transferOperationsHelper.processPutOfSingleFile(sourceFile,
						targetIrodsFile, transferStatusCallbackListener,
						operativeTransferControlBlock);

				// send status callback that indicates completion
				if (transferStatusCallbackListener != null) {
					TransferStatus status = TransferStatus.instance(
							TransferType.PUT, sourceFile.getAbsolutePath(),
							callbackTargetIrodsPath, "",
							operativeTransferControlBlock
									.getTotalBytesToTransfer(),
							operativeTransferControlBlock
									.getTotalBytesTransferredSoFar(),
							operativeTransferControlBlock
									.getTotalFilesTransferredSoFar(),
							operativeTransferControlBlock
									.getTotalFilesToTransfer(),
							TransferState.OVERALL_COMPLETION,this.getIRODSAccount().getHost(),
							this.getIRODSAccount().getZone());
					transferStatusCallbackListener
							.overallStatusCallback(status);
				}

			}
		} catch (JargonException je) {
			log.warn(
					"unexpected exception in put operation that should have been caught in the transfer handler",
					je);
			processExceptionDuringPutOperation(sourceFile, targetIrodsFile,
					transferStatusCallbackListener,
					operativeTransferControlBlock, je);
		}
	}

	/**
	 * @param transferControlBlock
	 * @return
	 * @throws JargonException
	 */
	private TransferControlBlock buildTransferControlBlockAndOptionsBasedOnParameters(
			final TransferControlBlock transferControlBlock)
			throws JargonException {
		
		TransferControlBlock operativeTransferControlBlock = transferControlBlock;
		
		if (operativeTransferControlBlock == null) {
			log.info("creating default transfer control block, none was supplied and a callback listener is set");
			operativeTransferControlBlock = DefaultTransferControlBlock
					.instance();
		}
		
		if (operativeTransferControlBlock.getTransferOptions() == null) {
			operativeTransferControlBlock.setTransferOptions(getIRODSSession().buildTransferOptionsBasedOnJargonProperties());
		}
		return operativeTransferControlBlock;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.irods.jargon.core.pub.DataTransferOperations#putOperation(java.lang
	 * .String, java.lang.String, java.lang.String,
	 * org.irods.jargon.core.transfer.TransferStatusCallbackListener,
	 * org.irods.jargon.core.transfer.TransferControlBlock)
	 */
	@Override
	public void putOperation(
			final String sourceFileAbsolutePath,
			final String targetIrodsFileAbsolutePath,
			String targetResourceName,
			final TransferStatusCallbackListener transferStatusCallbackListener,
			final TransferControlBlock transferControlBlock)
			throws JargonException {

		if (sourceFileAbsolutePath == null || sourceFileAbsolutePath.isEmpty()) {
			throw new IllegalArgumentException(
					"sourceFileAbsolutePath is null or empty");
		}

		if (targetIrodsFileAbsolutePath == null
				|| targetIrodsFileAbsolutePath.isEmpty()) {
			throw new IllegalArgumentException(
					"targetIrodsFileAbsolutePath is null or empty");
		}

		if (targetResourceName == null) {
			throw new IllegalArgumentException("targetResourceName is null");
		}

		if (targetResourceName.isEmpty()) {
			targetResourceName = getIRODSAccount().getDefaultStorageResource();
		}

		log.info("put operation for source: {}", sourceFileAbsolutePath);
		log.info(" to target: {}", targetIrodsFileAbsolutePath);
		log.info("  resource:{}", targetResourceName);

		File sourceFile = new File(sourceFileAbsolutePath);
		IRODSFile targetFile = this.getIRODSFileFactory().instanceIRODSFile(
				targetIrodsFileAbsolutePath);
		targetFile.setResource(targetResourceName);

		putOperation(sourceFile, targetFile, transferStatusCallbackListener,
				transferControlBlock);
	}

	/**
	 * @param sourceFile
	 * @param operativeTransferControlBlock
	 */
	private void preCountLocalFilesBeforeTransfer(final File sourceFile,
			final TransferControlBlock operativeTransferControlBlock) {
		if (operativeTransferControlBlock != null) {
			int fileCount = LocalFileUtils.countFilesInDirectory(sourceFile);
			log.info("put will transfer {} files)", fileCount);
			operativeTransferControlBlock.setTotalFilesToTransfer(fileCount);
		}
	}

	/**
	 * An exception has occurred in a put operation. This method will check for
	 * the existence of a callback listener for status. If one is supplied, the
	 * exception is reported to the callback listener and quashed. If no
	 * callback listener was supplied, the error is rethrown to the caller.
	 * 
	 * @param sourceFile
	 * @param targetIrodsFile
	 * @param transferStatusCallbackListener
	 *            {@link org.irods.jargon.core.transfer.TransferStatusCallbackListener}
	 *            implementation that will receive callbacks of success/failure
	 *            of each individual file transfer. This may be set to
	 *            <code>null</code>, in which case, exceptions that are thrown
	 *            will be rethrown by this method to the caller.
	 * @param transferControlBlock
	 *            {@link org.irods.jargon.core.transfer.TransferControlBlock}
	 *            implementation that is the communications mechanism between
	 *            the initiator of the transfer and the transfer process. This
	 *            may be set to <code>null</code> if those facilities are not
	 *            needed.
	 * @param je
	 * @throws JargonException
	 */
	private void processExceptionDuringPutOperation(
			final File sourceFile,
			final IRODSFile targetIrodsFile,
			final TransferStatusCallbackListener transferStatusCallbackListener,
			final TransferControlBlock transferControlBlock,
			final JargonException je) throws JargonException {

		log.error("exception in transfer", je);

		int totalFiles = 0;
		int totalFilesSoFar = 0;

		if (transferControlBlock != null) {
			transferControlBlock.reportErrorInTransfer();
			totalFiles = transferControlBlock.getTotalFilesToTransfer();
			totalFilesSoFar = transferControlBlock
					.getTotalFilesTransferredSoFar();
		}

		if (transferStatusCallbackListener != null) {
			log.warn("exception will be passed back to existing callback listener");

			TransferStatus status = TransferStatus.instanceForException(
					TransferType.PUT, sourceFile.getAbsolutePath(),
					targetIrodsFile.getAbsolutePath(), "", sourceFile.length(),
					sourceFile.length(), totalFilesSoFar, totalFiles, je,this.getIRODSAccount().getHost(),
					this.getIRODSAccount().getZone());

			transferStatusCallbackListener.statusCallback(status);

		} else {
			log.warn("exception will be re-thrown, as there is no status callback listener");
			throw je;

		}
	}

	/**
	 * A put operation has been initiated for a directory. This means that
	 * Jargon will recursively process the put operation. The containing folder
	 * of the source will be used as the new subdirectory in iRODS under which
	 * the data will be moved.
	 * 
	 * @param sourceFile
	 *            <code>File</code> that is the source of the transfer.
	 * @param targetIrodsFile
	 *            {@link org.irods.jargon.core.pub.io.IRODSFile} that is the
	 *            target of the transfer.
	 * @param transferStatusCallbackListener
	 *            {@link org.irods.jargon.core.transfer.TransferStatusCallbackListener}
	 *            implementation that will receive callbacks of success/failure
	 *            of each individual file transfer. This may be set to
	 *            <code>null</code>, in which case, exceptions that are thrown
	 *            will be rethrown by this method to the caller.
	 * @param transferControlBlock
	 *            {@link org.irods.jargon.core.transfer.TransferControlBlock}
	 *            implementation that is the communications mechanism between
	 *            the initiator of the transfer and the transfer process. This
	 *            may be set to <code>null</code> if those facilities are not
	 *            needed.
	 * @throws JargonException
	 */
	private void putWhenSourceFileIsDirectory(
			final File sourceFile,
			final IRODSFile targetIrodsFile,
			final TransferStatusCallbackListener transferStatusCallbackListener,
			final TransferControlBlock transferControlBlock)
			throws JargonException {

		log.info("this put operation is recursive");

		/*
		 * take the last path of the source, and make this a directory under
		 * iRODS. Then, the files will begin transfer under this newly created
		 * subdirectory
		 */

		if (targetIrodsFile.exists() && !targetIrodsFile.isDirectory()) {
			String msg = "attempt to put a collection (recursively) to a target iRODS file that is not a collection";
			log.error(msg);
			throw new JargonException(msg);
		}

		String thisDirName = sourceFile.getName();
		log.info("this dir name, will be the new parent directory in iRODS:{}",
				thisDirName);

		IRODSFile newIrodsParentDirectory = getIRODSFileFactory()
				.instanceIRODSFile(targetIrodsFile.getAbsolutePath(),
						thisDirName);
		newIrodsParentDirectory.setResource(targetIrodsFile.getResource());
		
		// send 0th file status callback that indicates startup
		if (transferStatusCallbackListener != null) {
			TransferStatus status = TransferStatus.instance(
					TransferType.PUT, sourceFile.getAbsolutePath(),
					newIrodsParentDirectory.getAbsolutePath(), "",
					transferControlBlock
							.getTotalBytesToTransfer(),
							transferControlBlock
							.getTotalBytesTransferredSoFar(),
							transferControlBlock
							.getTotalFilesTransferredSoFar(),
							transferControlBlock
							.getTotalFilesToTransfer(),
					TransferState.OVERALL_INITIATION,this.getIRODSAccount().getHost(),
					this.getIRODSAccount().getZone());
			transferStatusCallbackListener
					.overallStatusCallback(status);
		}


		try {
			newIrodsParentDirectory.mkdir();
		} catch (Exception e) {
			log.error("exeption in mkdir of: {}",
					newIrodsParentDirectory.getAbsolutePath());
			throw new JargonException(e);
		}

		transferOperationsHelper.recursivelyPut(sourceFile,
				newIrodsParentDirectory, transferStatusCallbackListener,
				transferControlBlock);
		
		// send status callback that indicates completion
		if (transferStatusCallbackListener != null) {
			TransferStatus status = TransferStatus.instance(
					TransferType.PUT, sourceFile.getAbsolutePath(),
					newIrodsParentDirectory.getAbsolutePath(), "",
					transferControlBlock
							.getTotalBytesToTransfer(),
							transferControlBlock
							.getTotalBytesTransferredSoFar(),
							transferControlBlock
							.getTotalFilesTransferredSoFar(),
							transferControlBlock
							.getTotalFilesToTransfer(),
					TransferState.OVERALL_COMPLETION,this.getIRODSAccount().getHost(),
					this.getIRODSAccount().getZone());
			transferStatusCallbackListener
					.overallStatusCallback(status);
		}
	}


	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.irods.jargon.core.pub.DataTransferOperations#replicate(java.lang.
	 * String, java.lang.String,
	 * org.irods.jargon.core.transfer.TransferStatusCallbackListener,
	 * org.irods.jargon.core.transfer.TransferControlBlock)
	 */
	@Override
	public void replicate(
			final String irodsFileAbsolutePath,
			final String targetResource,
			final TransferStatusCallbackListener transferStatusCallbackListener,
			final TransferControlBlock transferControlBlock)
			throws JargonException {

		if (irodsFileAbsolutePath == null || irodsFileAbsolutePath.isEmpty()) {
			throw new JargonException("irodsFileAbsolutePath is null or empty");
		}

		if (targetResource == null || targetResource.isEmpty()) {
			throw new JargonException("target resource is null or empty");
		}

		log.info("replicate operation for source: {}", irodsFileAbsolutePath);
		log.info(" to target resource: {}", targetResource);

		TransferControlBlock operativeTransferControlBlock = buildTransferControlBlockAndOptionsBasedOnParameters(
				transferControlBlock);
		
		final IRODSFileFactory irodsFileFactory = this.getIRODSFileFactory();

		IRODSFile sourceFile = irodsFileFactory
				.instanceIRODSFile(irodsFileAbsolutePath);

		// look for recursive put (directory to collection) and process,
		// otherwise, just put the file

		if (sourceFile.isDirectory()) {
			log.info("this replication operation is recursive");

			preCountIrodsFilesBeforeTransfer(irodsFileAbsolutePath,
					operativeTransferControlBlock);

			// send 0th file status callback that indicates startup
			if (transferStatusCallbackListener != null) {
				TransferStatus status = TransferStatus
						.instance(TransferType.REPLICATE, sourceFile
								.getAbsolutePath(), "", targetResource,
								operativeTransferControlBlock
										.getTotalBytesToTransfer(),
								operativeTransferControlBlock
										.getTotalBytesTransferredSoFar(),
								operativeTransferControlBlock
										.getTotalFilesTransferredSoFar(),
								operativeTransferControlBlock
										.getTotalFilesToTransfer(),
								TransferState.OVERALL_INITIATION,this.getIRODSAccount().getHost(),
								this.getIRODSAccount().getZone());
				transferStatusCallbackListener.overallStatusCallback(status);
			}

			transferOperationsHelper.recursivelyReplicate(sourceFile,
					targetResource, transferStatusCallbackListener,
					operativeTransferControlBlock);

			// send completion status callback
			if (transferStatusCallbackListener != null) {
				TransferStatus status = TransferStatus
						.instance(TransferType.REPLICATE, sourceFile
								.getAbsolutePath(), "", targetResource,
								operativeTransferControlBlock
										.getTotalBytesToTransfer(),
								operativeTransferControlBlock
										.getTotalBytesTransferredSoFar(),
								operativeTransferControlBlock
										.getTotalFilesTransferredSoFar(),
								operativeTransferControlBlock
										.getTotalFilesToTransfer(),
								TransferState.OVERALL_COMPLETION,this.getIRODSAccount().getHost(),
								this.getIRODSAccount().getZone());
				transferStatusCallbackListener.overallStatusCallback(status);
			}

		} else {
			
				operativeTransferControlBlock.setTotalFilesToTransfer(1);
			
			// send 0th file status callback that indicates startup
			if (transferStatusCallbackListener != null) {
				TransferStatus status = TransferStatus
						.instance(TransferType.REPLICATE, sourceFile
								.getAbsolutePath(), "", targetResource,
								operativeTransferControlBlock
										.getTotalBytesToTransfer(),
								operativeTransferControlBlock
										.getTotalBytesTransferredSoFar(),
								operativeTransferControlBlock
										.getTotalFilesTransferredSoFar(),
								operativeTransferControlBlock
										.getTotalFilesToTransfer(),
								TransferState.OVERALL_INITIATION,this.getIRODSAccount().getHost(),
								this.getIRODSAccount().getZone());
				transferStatusCallbackListener.overallStatusCallback(status);
			}

			processReplicationOfSingleFile(irodsFileAbsolutePath,
					targetResource, transferStatusCallbackListener,
					operativeTransferControlBlock);

			// send completion status callback
			if (transferStatusCallbackListener != null) {
				TransferStatus status = TransferStatus
						.instance(TransferType.REPLICATE, sourceFile
								.getAbsolutePath(), "", targetResource,
								operativeTransferControlBlock
										.getTotalBytesToTransfer(),
								operativeTransferControlBlock
										.getTotalBytesTransferredSoFar(),
								operativeTransferControlBlock
										.getTotalFilesTransferredSoFar(),
								operativeTransferControlBlock
										.getTotalFilesToTransfer(),
								TransferState.OVERALL_COMPLETION,this.getIRODSAccount().getHost(),
								this.getIRODSAccount().getZone());
				transferStatusCallbackListener.overallStatusCallback(status);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.irods.jargon.core.pub.DataTransferOperations#copy(java.lang.String,
	 * java.lang.String, java.lang.String,
	 * org.irods.jargon.core.transfer.TransferStatusCallbackListener, boolean,
	 * org.irods.jargon.core.transfer.TransferControlBlock)
	 */
	@Override
	public void copy(
			final String irodsSourceFileAbsolutePath,
			final String targetResource,
			final String irodsTargetFileAbsolutePath,
			final TransferStatusCallbackListener transferStatusCallbackListener,
			final boolean force, final TransferControlBlock transferControlBlock)
			throws JargonException {

		if (irodsSourceFileAbsolutePath == null
				|| irodsSourceFileAbsolutePath.isEmpty()) {
			throw new IllegalArgumentException(
					"irodsSourceFileAbsolutePath is null or empty");
		}

		if (irodsTargetFileAbsolutePath == null
				|| irodsTargetFileAbsolutePath.isEmpty()) {
			throw new IllegalArgumentException(
					"irodsTargetFileAbsolutePath is null or empty");
		}

		if (targetResource == null) {
			throw new IllegalArgumentException(
					"target resource is null or empty");
		}

		log.info("copy operation for source: {}", irodsSourceFileAbsolutePath);
		log.info("to target file:{}", irodsTargetFileAbsolutePath);
		log.info(" to target resource: {}", targetResource);

		TransferControlBlock operativeTransferControlBlock = transferControlBlock;

		/*
		 * If a callback listener is given, make sure there is a transfer
		 * control block, and do a pre-scan of the iRODS collection to get a
		 * count of files to be copied
		 */

		if (transferStatusCallbackListener != null) {
			if (transferControlBlock == null) {
				log.info("creating default transfer control block, none was supplied and a callback listener is set");
				operativeTransferControlBlock = DefaultTransferControlBlock
						.instance();
			}
		}

		IRODSFileFactory irodsFileFactory = this.getIRODSFileFactory();
		IRODSFile sourceFile = irodsFileFactory
				.instanceIRODSFile(irodsSourceFileAbsolutePath);
		IRODSFile targetFile = this.getIRODSFileFactory().instanceIRODSFile(
				irodsTargetFileAbsolutePath);

		// look for recursive copy (collection to collection) and process,
		// otherwise, just copy the file

		if (sourceFile.isDirectory()) {
			processCopyWhenSourceIsDir(targetResource,
					transferStatusCallbackListener, force,
					operativeTransferControlBlock, sourceFile, targetFile);

		} else {
			processCopyWhenSourceIsAFile(targetResource,
					transferStatusCallbackListener, force,
					operativeTransferControlBlock, sourceFile, targetFile);
		}
	}

	/**
	 * @param targetResource
	 * @param transferStatusCallbackListener
	 * @param force
	 * @param operativeTransferControlBlock
	 * @param sourceFile
	 * @param targetFile
	 * @throws DuplicateDataException
	 * @throws JargonException
	 */
	private void processCopyWhenSourceIsAFile(
			final String targetResource,
			final TransferStatusCallbackListener transferStatusCallbackListener,
			final boolean force,
			final TransferControlBlock operativeTransferControlBlock,
			final IRODSFile sourceFile, IRODSFile targetFile)
			throws DuplicateDataException, JargonException {
		if (targetFile.getAbsolutePath().equals(sourceFile.getParent())) {
			log.error("source file is being copied to own parent:{}",
					sourceFile.getAbsolutePath());
			throw new DuplicateDataException(
					"attempt to copy source file to its parent");
		}

		if (operativeTransferControlBlock != null) {
			operativeTransferControlBlock.setTotalFilesToTransfer(1);
		}

		if (targetFile.isDirectory()) {
			targetFile = getIRODSFileFactory().instanceIRODSFile(
					targetFile.getAbsolutePath(), sourceFile.getName());
			targetFile.setResource(targetResource);
			log.info("file name normailzed:{}", targetFile);
		}

		// send 0th file status callback that indicates startup
		if (transferStatusCallbackListener != null) {
			TransferStatus status = TransferStatus.instance(TransferType.COPY,
					sourceFile.getAbsolutePath(), targetFile.getAbsolutePath(),
					targetResource, operativeTransferControlBlock
							.getTotalBytesToTransfer(),
					operativeTransferControlBlock
							.getTotalBytesTransferredSoFar(),
					operativeTransferControlBlock
							.getTotalFilesTransferredSoFar(),
					operativeTransferControlBlock.getTotalFilesToTransfer(),
					TransferState.OVERALL_INITIATION,this.getIRODSAccount().getHost(),
					this.getIRODSAccount().getZone());
			transferStatusCallbackListener.overallStatusCallback(status);
		}

		transferOperationsHelper.processCopyOfSingleFile(
				sourceFile.getAbsolutePath(), targetResource,
				targetFile.getAbsolutePath(), force,
				transferStatusCallbackListener, operativeTransferControlBlock);

		// send status callback that indicates completion of the process
		if (transferStatusCallbackListener != null) {
			TransferStatus status = TransferStatus.instance(TransferType.COPY,
					sourceFile.getAbsolutePath(), targetFile.getAbsolutePath(),
					targetResource, operativeTransferControlBlock
							.getTotalBytesToTransfer(),
					operativeTransferControlBlock
							.getTotalBytesTransferredSoFar(),
					operativeTransferControlBlock
							.getTotalFilesTransferredSoFar(),
					operativeTransferControlBlock.getTotalFilesToTransfer(),
					TransferState.OVERALL_COMPLETION,this.getIRODSAccount().getHost(),
					this.getIRODSAccount().getZone());
			transferStatusCallbackListener.overallStatusCallback(status);
		}
	}

	/**
	 * @param targetResource
	 * @param transferStatusCallbackListener
	 * @param force
	 * @param operativeTransferControlBlock
	 * @param sourceFile
	 * @param targetFile
	 * @throws JargonException
	 * @throws DuplicateDataException
	 */
	private void processCopyWhenSourceIsDir(

			final String targetResource,
			final TransferStatusCallbackListener transferStatusCallbackListener,
			final boolean force,
			final TransferControlBlock operativeTransferControlBlock,
			final IRODSFile sourceFile, IRODSFile targetFile)
			throws JargonException, DuplicateDataException {
		log.info("this copy operation is recursive");

		preCountIrodsFilesBeforeTransfer(sourceFile.getAbsolutePath(),
				operativeTransferControlBlock);

		// The source directory becomes the new target subdirectory
		if (targetFile.getAbsolutePath().equals(sourceFile.getParent())) {
			log.error("source file is being copied to own parent:{}",
					sourceFile.getAbsolutePath());
			throw new DuplicateDataException(
					"attempt to copy source file to its parent");
		}

		// if the target is a file, use the parent
		if (targetFile.exists() && targetFile.isFile()) {
			targetFile = (IRODSFile) targetFile.getParentFile();
			log.info("target of copy is a file, path switched to parent: {}",
					targetFile.getAbsolutePath());
		}

		// here I know the source file is a collection
		targetFile = this.getIRODSFileFactory().instanceIRODSFile(
				targetFile.getAbsolutePath(), sourceFile.getName());

		log.info(
				"resolved target file with appended source file collection name is: {}",
				targetFile.getAbsolutePath());
		targetFile.mkdirs();
		log.info("any necessary subdirs created for target file");

		// send 0th file status callback that indicates startup
		if (transferStatusCallbackListener != null) {
			TransferStatus status = TransferStatus.instance(TransferType.COPY,
					sourceFile.getAbsolutePath(), targetFile.getAbsolutePath(),
					targetResource, operativeTransferControlBlock
							.getTotalBytesToTransfer(),
					operativeTransferControlBlock
							.getTotalBytesTransferredSoFar(),
					operativeTransferControlBlock
							.getTotalFilesTransferredSoFar(),
					operativeTransferControlBlock.getTotalFilesToTransfer(),
					TransferState.OVERALL_INITIATION,this.getIRODSAccount().getHost(),
					this.getIRODSAccount().getZone());
			transferStatusCallbackListener.overallStatusCallback(status);
		}

		transferOperationsHelper.recursivelyCopy(sourceFile, targetResource,
				targetFile.getAbsolutePath(), force,
				transferStatusCallbackListener, operativeTransferControlBlock);

		// send status callback that indicates completion of the process
		if (transferStatusCallbackListener != null) {
			TransferStatus status = TransferStatus.instance(TransferType.COPY,
					sourceFile.getAbsolutePath(), targetFile.getAbsolutePath(),
					targetResource, operativeTransferControlBlock
							.getTotalBytesToTransfer(),
					operativeTransferControlBlock
							.getTotalBytesTransferredSoFar(),
					operativeTransferControlBlock
							.getTotalFilesTransferredSoFar(),
					operativeTransferControlBlock.getTotalFilesToTransfer(),
					TransferState.OVERALL_COMPLETION,this.getIRODSAccount().getHost(),
					this.getIRODSAccount().getZone());
			transferStatusCallbackListener.overallStatusCallback(status);
		}
	}

	/**
	 * Do a pre-transfer count of files for progress indications.
	 * 
	 * @param irodsFileAbsolutePath
	 * @param operativeTransferControlBlock
	 * @throws JargonException
	 */
	private void preCountIrodsFilesBeforeTransfer(
			final String irodsFileAbsolutePath,
			final TransferControlBlock operativeTransferControlBlock)
			throws JargonException {
		if (operativeTransferControlBlock != null) {
			IRODSAccessObjectFactory irodsAccessObjectFactory = this
					.getIRODSAccessObjectFactory();
			CollectionAO collectionAO = irodsAccessObjectFactory
					.getCollectionAO(getIRODSAccount());
			int fileCount = collectionAO
					.countAllFilesUnderneathTheGivenCollection(irodsFileAbsolutePath);
			log.info("replication operation for {} files)", fileCount);
			operativeTransferControlBlock.setTotalFilesToTransfer(fileCount);
		}
	}

	/**
	 * Replicate a single file and process any exceptions or success callbacks.
	 * 
	 * @param irodsFileAbsolutePath
	 * @param targetResource
	 * @param transferStatusCallbackListener
	 *            {@link org.irods.jargon.core.transfer.TransferStatusCallbackListener}
	 *            implementation that will receive callbacks of success/failure
	 *            of each individual file transfer. This may be set to
	 *            <code>null</code>, in which case, exceptions that are thrown
	 *            will be rethrown by this method to the caller.
	 * @param transferControlBlock
	 *            {@link org.irods.jargon.core.transfer.TransferControlBlock}
	 *            implementation that is the communications mechanism between
	 *            the initiator of the transfer and the transfer process. This
	 *            may be set to <code>null</code> if those facilities are not
	 *            needed.
	 * @throws JargonException
	 */
	private void processReplicationOfSingleFile(
			final String irodsFileAbsolutePath,
			final String targetResource,
			final TransferStatusCallbackListener transferStatusCallbackListener,
			final TransferControlBlock transferControlBlock)
			throws JargonException {
		log.info("replicate single file");

		transferOperationsHelper.processReplicationOfSingleFile(
				irodsFileAbsolutePath, targetResource,
				transferStatusCallbackListener, transferControlBlock);
	}

}
