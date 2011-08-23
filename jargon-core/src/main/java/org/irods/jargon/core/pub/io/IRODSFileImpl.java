/**
 * 
 */
package org.irods.jargon.core.pub.io;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.irods.jargon.core.exception.DataNotFoundException;
import org.irods.jargon.core.exception.DuplicateDataException;
import org.irods.jargon.core.exception.JargonException;
import org.irods.jargon.core.exception.JargonFileOrCollAlreadyExistsException;
import org.irods.jargon.core.exception.JargonRuntimeException;
import org.irods.jargon.core.packinstr.DataObjInp;
import org.irods.jargon.core.pub.IRODSFileSystemAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Describes a file or collection on the IRODS data grid. Note that
 * <code>IRODSFileImpl</code> is a variant of an
 * {@link org.irogs.jargon.core.pub.IRODSAccessObject IRODSAccessObject}, and
 * internally holds a connection to IRODS.
 * <p/>
 * This object is not thread-safe, and cannot be shared between threads. This
 * File object has a connection associated with the thread which created it.
 * There are methods in {@link org.irods.jargon.core.pub.io.IRODSFileFactory
 * IRODSFileFactory} that allow an <code>IRODSFileImpl</code> to be attached to
 * another Thread and connection.
 * 
 * @author Mike Conway - DICE (www.irods.org)
 * 
 */

public final class IRODSFileImpl extends File implements IRODSFile {

	static Logger log = LoggerFactory.getLogger(IRODSFileImpl.class);

	IRODSFileSystemAO irodsFileSystemAO = null;

	private String fileName = "";
	private String resource = "";
	private int fileDescriptor = -1;
	private List<String> directory = new ArrayList<String>();
	private PathNameType pathNameType = PathNameType.UNKNOWN;
	private long length = -1;

	private static final long serialVersionUID = -6986662136294659059L;

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#reset()
	 */
	@Override
	public void reset() {
		this.length = -1;
		this.pathNameType = PathNameType.UNKNOWN;
	}

	protected IRODSFileImpl(final String pathName,
			final IRODSFileSystemAO irodsFileSystemAO) throws JargonException {
		this("", pathName, irodsFileSystemAO);
		if (pathName == null || pathName.isEmpty()) {
			throw new JargonException("path name is null or empty");
		}
	}

	/**
	 * Constructor that can preset the file type, thus avoiding a GenQuery
	 * lookup when <code>isFile</code> is subsequently called
	 * 
	 * @param pathName
	 * @param irodsFileSystemAO
	 * @param isFile
	 * @throws JargonException
	 */
	protected IRODSFileImpl(final String pathName,
			final IRODSFileSystemAO irodsFileSystemAO, final boolean isFile)
			throws JargonException {
		this("", pathName, irodsFileSystemAO);
		if (isFile) {
			pathNameType = PathNameType.FILE;
		} else {
			pathNameType = PathNameType.DIRECTORY;
		}
	}

	/**
	 * Constructor with parent and child name that can preset the file type,
	 * thus avoiding a GenQuery lookup when <code>isFile</code> is subsequently
	 * called.
	 * 
	 * @param parentName
	 * @param childName
	 * @param irodsFileSystemAO
	 * @param isFile
	 * @throws JargonException
	 */
	protected IRODSFileImpl(final String parentName, final String childName,
			final IRODSFileSystemAO irodsFileSystemAO, final boolean isFile)
			throws JargonException {
		this(parentName, childName, irodsFileSystemAO);
		if (isFile) {
			pathNameType = PathNameType.FILE;
		} else {
			pathNameType = PathNameType.DIRECTORY;
		}
	}

	protected IRODSFileImpl(final String parent, final String child,
			final IRODSFileSystemAO irodsFileSystemAO) throws JargonException {

		super(parent, child);

		if (irodsFileSystemAO == null) {
			throw new IllegalArgumentException("irodsFileSystemAO is null");
		}

		if (parent == null) {
			throw new IllegalArgumentException("null or missing parent name");
		}

		if (child == null) {
			throw new IllegalArgumentException("null child name");
		}

		if (parent.isEmpty() && child.isEmpty()) {
			throw new IllegalArgumentException(
					"both parent and child names are empty");
		}

		this.irodsFileSystemAO = irodsFileSystemAO;
		setDirectory(parent);
		setFileName(child);
		makePathCanonical(parent);
	}

	protected IRODSFileImpl(final File parent, final String child,
			final IRODSFileSystemAO irodsFileSystemAO) throws JargonException {

		this(parent.getAbsolutePath(), child, irodsFileSystemAO);
	}

	/**
	 * @param dir
	 *            Used to determine if the path is absolute.
	 */

	private void makePathCanonical(String dir) {
		int i = 0; // where to insert into the Vector
		boolean absolutePath = false;
		String canonicalTest = null;

		if (dir == null) {
			dir = "";
		}

		// In case this abstract path is supposed to be root
		if ((fileName.equals(IRODS_ROOT) || fileName.equals(""))
				&& dir.equals("")) {
			return;
		}

		// In case this abstract path is supposed to be the home directory
		if (fileName.equals("") && dir.equals("")) {
			String home = irodsFileSystemAO.getIRODSAccount()
					.getHomeDirectory();
			int index = home.lastIndexOf(PATH_SEPARATOR);
			setDirectory(home.substring(0, index));
			setFileName(home.substring(index + 1));
			return;
		}

		// if dir not absolute
		if (dir.startsWith(IRODS_ROOT)) {
			absolutePath = true;
		}

		// if directory not already absolute
		if (directory.size() > 0) {
			if (directory.get(0).toString().length() == 0) {
				// The /'s were all striped when the vector was created
				// so if the first element of the vector is null
				// but the vector isn't null, then the first element
				// is really a /.
				absolutePath = true;
			}
		}
		if (!absolutePath) {
			String home = irodsFileSystemAO.getIRODSAccount()
					.getHomeDirectory();
			int index = home.indexOf(PATH_SEPARATOR);
			// allow the first index to = 0,
			// because otherwise separator won't get added in front.
			if (index >= 0) {
				do {
					directory.add(i, home.substring(0, index));
					home = home.substring(index + 1);
					index = home.indexOf(PATH_SEPARATOR);
					i++;
				} while (index > 0);
			}
			if ((!home.equals("")) && (home != null)) {
				directory.add(i, home);
			}
		}

		// first, made absolute, then canonical
		for (i = 0; i < directory.size(); i++) {
			canonicalTest = directory.get(i).toString();
			if (canonicalTest.equals(".")) {
				directory.remove(i);
				i--;
			} else if ((canonicalTest.equals("..")) && (i >= 2)) {
				directory.remove(i);
				directory.remove(i - 1);
				i--;
				if (i > 0) {
					i--;
				}
			} else if (canonicalTest.equals("..")) {
				// at root, just remove the ..
				directory.remove(i);
				i--;
			} else if (canonicalTest.startsWith(separator)) {
				// if somebody put filepath as /foo//bar or /foo////bar
				do {
					canonicalTest = canonicalTest.substring(1);
				} while (canonicalTest.startsWith(PATH_SEPARATOR));
				directory.remove(i);
				directory.add(i, canonicalTest);
			}
		}
		// also must check fileName
		if (fileName.equals(".")) {
			fileName = directory.get(directory.size() - 1).toString();
			directory.remove(directory.size() - 1);
		} else if (fileName.equals("..")) {
			if (directory.size() > 1) {
				fileName = directory.get(directory.size() - 2).toString();
				directory.remove(directory.size() - 1);
				directory.remove(directory.size() - 1);
			} else {
				// at root
				fileName = PATH_SEPARATOR;
				directory.remove(directory.size() - 1);
			}
		}
	}

	/**
	 * Set the directory.
	 * 
	 * @param dir
	 *            The directory path, need not be absolute.
	 */
	private void setDirectory(String dir) {
		if (directory == null) {
			directory = new ArrayList<String>();
		}

		// in case they used the local pathSeparator
		// in the fileName instead of the iRODS PATH_SEPARATOR.
		String localSeparator = System.getProperty("file.separator");
		int index = dir.lastIndexOf(localSeparator);
		if ((index >= 0) && ((dir.substring(index + 1).length()) > 0)) {
			dir = dir.substring(0, index) + PATH_SEPARATOR_CHAR
					+ dir.substring(index + 1);
			index = dir.lastIndexOf(localSeparator);
		}

		while ((directory.size() > 0) && dir.startsWith(PATH_SEPARATOR)) {
			dir = dir.substring(1);
			// problems if dir passed from filename starts with PATH_SEPARATOR
		}

		// create directory
		index = dir.indexOf(PATH_SEPARATOR_CHAR);

		if (index >= 0) {
			do {
				directory.add(dir.substring(0, index));
				do {
					dir = dir.substring(index + 1);
					index = dir.indexOf(PATH_SEPARATOR);
				} while (index == 0);
			} while (index >= 0);
		}
		// add the last path item
		if ((!dir.equals("")) && (dir != null)) {
			directory.add(dir);
		}
	}

	/**
	 * Set the file name.
	 * 
	 * @param fleName
	 *            The file name or fileName plus some or all of the directory
	 *            path.
	 */
	private void setFileName(String filePath) {

		// used when parsing the filepath
		int index;

		// in case they used the local pathSeparator
		// in the fileName instead of the iRODS PATH_SEPARATOR.
		String localSeparator = System.getProperty("file.separator");

		if (filePath == null) {
			throw new NullPointerException("The file name cannot be null");
		}

		log.info("setting file name, given path = {}", filePath);
		log.info("detected local separator = {}", localSeparator);

		// replace local separators with iRODS separators.
		if (!localSeparator.equals(PATH_SEPARATOR)) {
			index = filePath.lastIndexOf(localSeparator);
			while ((index >= 0)
					&& ((filePath.substring(index + 1).length()) > 0)) {
				filePath = filePath.substring(0, index) + PATH_SEPARATOR_CHAR
						+ filePath.substring(index + 1);
				index = filePath.lastIndexOf(localSeparator);
			}
		}
		fileName = filePath;

		if (fileName.length() > 1) { // add to allow path = root "/"
			index = fileName.lastIndexOf(PATH_SEPARATOR_CHAR);
			while ((index == fileName.length() - 1) && (index >= 0)) {
				// remove '/' at end of filename, if exists
				fileName = fileName.substring(0, index);
				index = fileName.lastIndexOf(PATH_SEPARATOR_CHAR);
			}

			// separate directory and file
			if ((index >= 0) && ((fileName.substring(index + 1).length()) > 0)) {
				// have to run setDirectory(...) again
				// because they put filepath info in the filename
				setDirectory(fileName.substring(0, index + 1));
				fileName = fileName.substring(index + 1);
			}
		}

		log.info("file name was set as: {}", fileName);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#canRead()
	 */
	@Override
	public boolean canRead() {
		try {
			return irodsFileSystemAO.isFileReadable(this);
		} catch (JargonException e) {
			String msg = "JargonException caught and rethrown as JargonRuntimeException:"
					+ e.getMessage();
			log.error(msg, e);
			throw new JargonRuntimeException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#canWrite()
	 */
	@Override
	public boolean canWrite() {
		boolean canWrite = false;
		try {
			canWrite = irodsFileSystemAO.isFileWriteable(this);
			log.info("checked if I could write this file, and got back:{}",
					canWrite);

		} catch (JargonException e) {
			String msg = "JargonException caught and rethrown as JargonRuntimeException:"
					+ e.getMessage();
			log.error(msg, e);
			throw new JargonRuntimeException(e);
		}
		return canWrite;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#createNewFile()
	 */
	@Override
	public boolean createNewFile() throws IOException {
		try {
			fileDescriptor = irodsFileSystemAO.createFile(
					this.getAbsolutePath(), DataObjInp.OpenFlags.READ_WRITE,
					DataObjInp.DEFAULT_CREATE_MODE);

			log.debug("file descriptor from new file create: {}",
					fileDescriptor);
			// in irods the file must be closed, then opened when doing a create
			// new
			this.close();
			this.open();
			log.debug("file now closed");
		} catch (JargonFileOrCollAlreadyExistsException e) {
			return false;
		} catch (JargonException e) {
			String msg = "JargonException caught and rethrown as IOException:"
					+ e.getMessage();
			log.error(msg, e);
			throw new IOException(e);
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#delete()
	 */
	@Override
	public boolean delete() {
		boolean successful = true;
		if (!exists()) {
			successful = true;
		} else {
			try {
				if (this.isFile()) {
					irodsFileSystemAO.fileDeleteNoForce(this);
				} else if (this.isDirectory()) {
					irodsFileSystemAO.directoryDeleteNoForce(this);
				}
			} catch (DataNotFoundException dnf) {
				successful = false;
			} catch (JargonException e) {

				log.error(
						"irods error occurred on delete, this was not a data not found exception, rethrow as unchecked",
						e);
				throw new JargonRuntimeException(
						"exception occurred on delete", e);

			}
		}
		return successful;

	}

	@Override
	public boolean deleteWithForceOption() {
		boolean successful = true;
		try {
			if (this.isFile()) {
				irodsFileSystemAO.fileDeleteForce(this);
			} else if (this.isDirectory()) {
				irodsFileSystemAO.directoryDeleteForce(this);
			}
		} catch (JargonException e) {
			String msg = "JargonException caught and logged on delete, method will return false and continue:"
					+ e.getMessage();
			log.error(msg, e);
			successful = false;
		}
		return successful;

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#deleteOnExit()
	 */
	@Override
	public void deleteOnExit() {
		throw new JargonRuntimeException(
				"delete on exit is not supported for IRODS Files, please explicitly delete the file");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object obj) {

		if (obj instanceof File) {
			File temp = (File) obj;
			return temp.getAbsolutePath().equals(this.getAbsolutePath());
		} else {
			return false;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#exists()
	 */
	@Override
	public boolean exists() {
		try {
			return irodsFileSystemAO.isFileExists(this);
		} catch (JargonException e) {
			String msg = "JargonException caught and rethrown as JargonRuntimeException:"
					+ e.getMessage();
			log.error(msg, e);
			throw new JargonRuntimeException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#getAbsoluteFile()
	 */
	@Override
	public File getAbsoluteFile() {

		try {
			return new IRODSFileImpl(getAbsolutePath(), this.irodsFileSystemAO);
		} catch (JargonException e) {
			String msg = "JargonException caught and rethrown as JargonRuntimeException:"
					+ e.getMessage();
			log.error(msg, e);
			throw new JargonRuntimeException(e);
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#getAbsolutePath()
	 */
	@Override
	public String getAbsolutePath() {
		StringBuilder pathBuilder = new StringBuilder();
		String builtPath = "";
		if ((directory != null) && (!directory.isEmpty())) {
			boolean firstPath = true;

			for (String element : directory) {
				if (!firstPath) {
					pathBuilder.append(PATH_SEPARATOR);
				}
				pathBuilder.append(element);
				firstPath = false;
			}

			pathBuilder.append(PATH_SEPARATOR);
			pathBuilder.append(getName());
			builtPath = pathBuilder.toString();
		} else {
			String name = getName();
			if (name == null || name.equals("")) {
				// just in case the dir and name are empty, return root.
				builtPath = IRODS_ROOT;
			} else {
				if (name.equals("/")) {
					builtPath = name;
				}
			}
		}
		return builtPath;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#getCanonicalFile()
	 */
	@Override
	public File getCanonicalFile() throws IOException {
		String canonicalPath = getCanonicalPath();
		try {
			return new IRODSFileImpl(canonicalPath, this.irodsFileSystemAO);
		} catch (JargonException e) {
			String msg = "jargon exception in file method, rethrown as IOException to match method signature"
					+ e.getMessage();
			log.error(msg, e);
			throw new IOException(msg, e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#getCanonicalPath()
	 */
	@Override
	public String getCanonicalPath() throws IOException {
		if ((directory != null) && (!directory.isEmpty())) {
			int size = directory.size();
			int i = 1;
			StringBuilder path = new StringBuilder();
			path.append(directory.get(0));

			while (i < size) {
				path.append(separator);
				path.append(directory.get(i));
				i++;
			}

			path.append(separator);
			path.append(fileName);
			return path.toString();
		}

		return fileName;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#getFreeSpace()
	 */
	@Override
	public long getFreeSpace() {
		// TODO: implement via quotas
		throw new UnsupportedOperationException();

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#getParentFile()
	 */
	@Override
	public File getParentFile() {
		String parentPath = getParent();

		if (parentPath == null) {
			return null;
		}

		try {
			return new IRODSFileImpl(parentPath, this.irodsFileSystemAO);
		} catch (JargonException e) {
			String msg = "jargon exception in file method, rethrown as JargonRuntimeException to match method signature"
					+ e.getMessage();
			log.error(msg, e);
			throw new JargonRuntimeException(msg, e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#getTotalSpace()
	 */
	@Override
	public long getTotalSpace() {
		// TODO: implement via quotas
		throw new UnsupportedOperationException();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#getUsableSpace()
	 */
	@Override
	public long getUsableSpace() {
		// TODO: implement via quotas
		throw new UnsupportedOperationException();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#hashCode()
	 */
	@Override
	public int hashCode() {
		return getAbsolutePath().toLowerCase().hashCode() ^ 1234321;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#isAbsolute()
	 */
	@Override
	public boolean isAbsolute() {
		// all path names are made absolute at construction.
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#isDirectory()
	 */
	@Override
	public boolean isDirectory() {
		if (pathNameType == PathNameType.UNKNOWN) {
			// do query
		} else if (pathNameType == PathNameType.FILE) {
			log.info("cached path says file");
			return false;
		} else if (pathNameType == PathNameType.DIRECTORY) {
			log.info("cached path says dir");
			return true;
		}

		boolean isDir;

		try {
			isDir = irodsFileSystemAO.isDirectory(this);
			if (isDir) {
				log.info("lookup via irodsFileSystemAO says this is a directory");
				pathNameType = PathNameType.DIRECTORY;
			} else {
				log.info("lookup via irodsFileSystemAO says this is a file");
				pathNameType = PathNameType.FILE;
			}
		} catch (DataNotFoundException dnf) {
			log.info("this is not a directory, calling unknown");
			pathNameType = PathNameType.UNKNOWN;

		} catch (JargonException je) {
			log.error("jargon exception, rethrow as unchecked", je);
			throw new JargonRuntimeException(je);
		}

		if (log.isDebugEnabled()) {
			log.debug("finally, pathNameType was:" + pathNameType);
		}

		return pathNameType == PathNameType.DIRECTORY;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#isFile()
	 */
	@Override
	public boolean isFile() {

		if (pathNameType == PathNameType.UNKNOWN) {
			// do query
		} else if (pathNameType == PathNameType.FILE) {
			return true;
		} else if (pathNameType == PathNameType.DIRECTORY) {
			return false;
		}

		if (!exists()) {
			log.debug("does not exist, this is not a file");
			return false;
		}

		boolean isDir;

		try {
			isDir = irodsFileSystemAO.isDirectory(this);
			if (isDir) {
				pathNameType = PathNameType.DIRECTORY;
			} else {
				pathNameType = PathNameType.FILE;
			}
		} catch (DataNotFoundException dnf) {
			log.info("this is not a file, calling unknown");
			pathNameType = PathNameType.UNKNOWN;

		} catch (JargonException je) {
			log.error("jargon exception, rethrow as unchecked", je);
			throw new JargonRuntimeException(je);
		}

		return pathNameType == PathNameType.FILE;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#getPath()
	 */
	@Override
	public String getPath() {
		return this.getAbsolutePath();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#isHidden()
	 */
	@Override
	public boolean isHidden() {
		return super.isHidden();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#lastModified()
	 */
	@Override
	public long lastModified() {
		try {
			return irodsFileSystemAO.getModificationDate(this);
		} catch (DataNotFoundException e) {
			return 0;
		} catch (JargonException e) {
			log.error("jargon exception, rethrow as unchecked", e);
			throw new JargonRuntimeException(e);

		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#length()
	 */
	@Override
	public long length() {

		if (length == -1) {
			log.info("caching new length val");

			try {
				length = irodsFileSystemAO.getLength(this);
			} catch (DataNotFoundException e) {
				length = 0;
			} catch (JargonException e) {
				log.error("jargon exception, rethrow as unchecked", e);
				throw new JargonRuntimeException(e);

			}
		}
		return length;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#list()
	 */
	@Override
	public String[] list() {
		try {
			List<String> result = irodsFileSystemAO.getListInDir(this);

			String[] a = new String[result.size()];
			return result.toArray(a);
		} catch (DataNotFoundException e) {
			return new String[] {};
		} catch (JargonException e) {
			log.error("jargon exception, rethrow as unchecked", e);
			throw new JargonRuntimeException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#list(java.io.FilenameFilter)
	 */
	@Override
	public String[] list(final FilenameFilter filter) {
		return super.list(filter);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#listFiles()
	 */
	@Override
	public File[] listFiles() {

		try {
			List<String> result = irodsFileSystemAO.getListInDir(this);
			IRODSFileImpl[] a = new IRODSFileImpl[result.size()];
			IRODSFileImpl irodsFile;
			int i = 0;
			for (String fileName : result) {
				// result has just the subdir under this file, need to create
				// the absolute path to create a file
				irodsFile = new IRODSFileImpl(this.getAbsolutePath(), fileName,
						this.irodsFileSystemAO);
				a[i++] = irodsFile;

			}
			return a;
		} catch (DataNotFoundException e) {
			return new IRODSFileImpl[] {};
		} catch (JargonException e) {
			log.error("jargon exception, rethrow as unchecked", e);
			throw new JargonRuntimeException(e);

		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#listFiles(java.io.FileFilter)
	 */
	@Override
	public File[] listFiles(final FileFilter filter) {
		try {
			List<File> result = irodsFileSystemAO.getListInDirWithFileFilter(
					this, filter);
			File[] resArray = new File[result.size()];
			return result.toArray(resArray);
		} catch (DataNotFoundException e) {
			return new IRODSFileImpl[] {};
		} catch (JargonException e) {
			log.error("jargon exception, rethrow as unchecked", e);
			e.printStackTrace();
			throw new JargonRuntimeException(e);

		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.irods.jargon.core.pub.io.IRODSFile#listFiles(java.io.FilenameFilter)
	 */
	@Override
	public File[] listFiles(final FilenameFilter filter) {
		try {
			List<String> result = irodsFileSystemAO.getListInDirWithFilter(
					this, new IRODSAcceptAllFileNameFilter());
			IRODSFileImpl[] a = new IRODSFileImpl[result.size()];
			IRODSFileImpl irodsFile;
			int i = 0;
			for (String fileName : result) {
				irodsFile = new IRODSFileImpl(fileName, this.irodsFileSystemAO);
				a[i++] = irodsFile;

			}
			return a;
		} catch (DataNotFoundException e) {
			return new IRODSFileImpl[] {};
		} catch (JargonException e) {
			log.error("jargon exception, rethrow as unchecked", e);
			throw new JargonRuntimeException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#mkdir()
	 */
	@Override
	public boolean mkdir() {

		try {
			irodsFileSystemAO.mkdir(this, false);
		} catch (DuplicateDataException e) {
			log.info("duplicate data exception, return false from mkdir", e);
			return false;
		} catch (JargonException e) {
			// check if this means that it already exists, and call that a
			// 'false' instead of an error
			if (e.getMessage().indexOf("-809000") > -1) {
				log.warn("directory already exists");
				return false;
			}
			log.error("jargon exception, rethrow as unchecked", e);
			throw new JargonRuntimeException(e);
		}

		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#mkdirs()
	 */
	@Override
	public boolean mkdirs() {
		try {
			irodsFileSystemAO.mkdir(this, true);
		} catch (DuplicateDataException e) {
			log.info("duplicate data exception, return false from mkdir", e);
			return false;
		} catch (JargonException e) {
			log.error("jargon exception, rethrow as unchecked", e);
			throw new JargonRuntimeException(e);
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#renameTo(java.io.File)
	 */
	@Override
	public boolean renameTo(final IRODSFile dest) {
		boolean success = false;
		if (dest == null) {
			String msg = "dest file is null";
			log.error(msg);
			throw new JargonRuntimeException(msg);
		}

		if (!(dest instanceof IRODSFileImpl)) {
			String msg = "provided dest file is not an instance of IRODSFileImpl, cannot rename";
			log.error(msg);
			throw new JargonRuntimeException(msg);
		}

		IRODSFile destIRODSFile = dest;

		if (log.isInfoEnabled()) {
			log.info("renaming:" + this.getAbsolutePath() + " to:"
					+ destIRODSFile.getAbsolutePath());
		}

		// if the path is different
		if (!getAbsolutePath().equals(dest.getAbsolutePath())) {
			renameFileOrDirectory(destIRODSFile);
			success = true;
		} else {
			// paths are the same, move to the new resource described by the
			// dest file
			log.info("doing a physical move");
			try {
				this.irodsFileSystemAO.physicalMove(this,
						destIRODSFile.getResource());
				success = true;
			} catch (JargonException e) {
				log.error("jargon exception, rethrow as unchecked", e);
				throw new JargonRuntimeException(e);
			}
		}
		return success;
	}

	/**
	 * @param destIRODSFile
	 * @throws JargonRuntimeException
	 */
	void renameFileOrDirectory(final IRODSFile destIRODSFile)
			throws JargonRuntimeException {
		if (isDirectory()) {
			log.info("paths different, and a directory is being renamed");
			try {
				this.irodsFileSystemAO.renameDirectory(this, destIRODSFile);
			} catch (JargonException e) {
				log.error("jargon exception, rethrow as unchecked", e);
				throw new JargonRuntimeException(e);
			}
		} else if (isFile()) {
			log.info("paths different, and a file is being renamed");
			try {
				this.irodsFileSystemAO.renameFile(this, destIRODSFile);
			} catch (JargonException e) {
				log.error("jargon exception, rethrow as unchecked", e);
				throw new JargonRuntimeException(e);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#setExecutable(boolean,
	 * boolean)
	 */
	@Override
	public boolean setExecutable(final boolean executable,
			final boolean ownerOnly) {
		throw new UnsupportedOperationException();

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#setExecutable(boolean)
	 */
	@Override
	public boolean setExecutable(final boolean executable) {
		throw new UnsupportedOperationException();

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#setLastModified(long)
	 */
	@Override
	public boolean setLastModified(final long time) {
		throw new UnsupportedOperationException();

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#setReadable(boolean, boolean)
	 */
	@Override
	public boolean setReadable(final boolean readable, final boolean ownerOnly) {
		throw new UnsupportedOperationException();

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#setReadable(boolean)
	 */
	@Override
	public boolean setReadable(final boolean readable) {
		throw new UnsupportedOperationException();

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#setReadOnly()
	 */
	@Override
	public boolean setReadOnly() {
		throw new UnsupportedOperationException();

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#setWritable(boolean, boolean)
	 */
	@Override
	public boolean setWritable(final boolean writable, final boolean ownerOnly) {
		throw new UnsupportedOperationException();

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#setWritable(boolean)
	 */
	@Override
	public boolean setWritable(final boolean writable) {
		throw new UnsupportedOperationException();

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#toString()
	 */
	@Override
	public String toString() {
		StringBuilder s = new StringBuilder();
		s.append("irods://");
		s.append(this.irodsFileSystemAO.getIRODSAccount().getUserName());
		s.append('@');
		s.append(this.irodsFileSystemAO.getIRODSAccount().getHost());
		s.append(':');
		s.append(this.irodsFileSystemAO.getIRODSAccount().getPort());
		s.append(getAbsolutePath());
		return s.toString();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#toURI()
	 */
	@Override
	public URI toURI() {
		URI uri = null;

		try {
			if (isDirectory()) {
				uri = new URI("irods", this.irodsFileSystemAO.getIRODSAccount()
						.getUserName(), this.irodsFileSystemAO
						.getIRODSAccount().getHost(), this.irodsFileSystemAO
						.getIRODSAccount().getPort(), getAbsolutePath(), null,
						null);
			} else {
				uri = new URI("irods", this.irodsFileSystemAO.getIRODSAccount()
						.getUserName(), this.irodsFileSystemAO
						.getIRODSAccount().getHost(), this.irodsFileSystemAO
						.getIRODSAccount().getPort(), getAbsolutePath(), null,
						null);
			}
		} catch (URISyntaxException e) {
			log.error("URISyntaxException, rethrow as unchecked", e);
			throw new JargonRuntimeException(e);
		}

		return uri;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#getResource()
	 */
	@Override
	public String getResource() throws JargonException {
		/*
		 * // I may have set the resource already if (resource.length() == 0) {
		 * // for files, get the actual resource associated with the file, //
		 * otherwise, // get any default set by the IRODS account if
		 * (this.isFile()) { resource =
		 * this.irodsFileSystemAO.getResourceNameForFile(this); } else {
		 * resource = this.irodsFileSystemAO.getIRODSAccount()
		 * .getDefaultStorageResource(); } } else { // note that there is some
		 * inconsistency between nulls and "" values // for resource, try and //
		 * standardize on null. This probably needs more work. }
		 */
		return resource;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#setResource(java.lang.String)
	 */
	@Override
	public void setResource(final String resource) {
		this.resource = resource;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#getFileDescriptor()
	 */
	@Override
	public int getFileDescriptor() {
		return fileDescriptor;
	}

	/**
	 * Set the iRODS file descriptor value. This will be set internally by
	 * Jargon.
	 * 
	 * @param fileDescriptor
	 */
	protected void setFileDescriptor(final int fileDescriptor) {
		this.fileDescriptor = fileDescriptor;
	}

	private int openWithMode(final DataObjInp.OpenFlags openFlags)
			throws JargonException {
		if (log.isInfoEnabled()) {
			log.info("opening irodsFile:" + this.getAbsolutePath());
		}

		if (!this.exists()) {
			throw new JargonException(
					"this file does not exist, so it cannot be opened.  The file should be created first!");
		}

		if (getFileDescriptor() > 0) {
			log.info("file is already open, use the given descriptor");
			return fileDescriptor;
		}

		int fileDescriptor = this.irodsFileSystemAO.openFile(this, openFlags);

		if (log.isDebugEnabled()) {
			log.debug("opened file with descriptor of:" + fileDescriptor);
		}

		this.fileDescriptor = fileDescriptor;
		return fileDescriptor;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#openReadOnly()
	 */
	@Override
	public int openReadOnly() throws JargonException {
		return openWithMode(DataObjInp.OpenFlags.READ);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#open()
	 */
	@Override
	public int open() throws JargonException {
		return openWithMode(DataObjInp.OpenFlags.READ_WRITE);

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#close()
	 */
	@Override
	public void close() throws JargonException {
		if (log.isInfoEnabled()) {
			log.info("closing irodsFile:" + this.getAbsolutePath());
		}

		if (this.getFileDescriptor() <= 0) {
			log.info("file is not open, silently ignore");
			this.setFileDescriptor(-1);
			return;
		}

		this.irodsFileSystemAO.fileClose(this.getFileDescriptor());
		this.setFileDescriptor(-1);

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#closeGivenDescriptor(int)
	 */
	@Override
	public void closeGivenDescriptor(final int fd) throws JargonException {
		if (log.isInfoEnabled()) {
			log.info("closing irodsFile given descriptor:" + fd);
		}

		if (fd <= 0) {
			log.info("file is not open, silently ignore");
			this.setFileDescriptor(-1);
			return;
		}

		this.irodsFileSystemAO.fileClose(fd);
		this.setFileDescriptor(-1);

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.core.pub.io.IRODSFile#compareTo(java.io.File)
	 */
	@Override
	public int compareTo(final IRODSFile pathname) {
		return (this.getAbsolutePath().compareTo(pathname.getAbsolutePath()));
	}

	@Override
	public String getName() {
		return fileName;
	}

	@Override
	public String getParent() {
		StringBuilder pathBuilder = new StringBuilder();
		if ((directory != null) && (!directory.isEmpty())) {
			int size = directory.size();
			pathBuilder.append(directory.get(0));
			int i = 1;

			while (i < size) {
				pathBuilder.append(PATH_SEPARATOR);
				pathBuilder.append(directory.get(i));
				i++;
			}

			// parent is /
			if (pathBuilder.length() == 0) {
				pathBuilder.append("/");
			}

			return pathBuilder.toString();
		} else {

			return null;
		}
	}

}
