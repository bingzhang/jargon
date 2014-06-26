package org.irods.jargon.core.checksum;

import org.irods.jargon.core.exception.JargonException;
import org.irods.jargon.core.protovalues.ChecksumEncodingEnum;

/**
 * Interface representing code to handle negotiation and determination of
 * checksum type
 * 
 * @author Mike Conway - DICXE
 *
 */
public interface ChecksumManager {

	/**
	 * Using the values in the jargon properties, as well as info on the target
	 * server, determine the type of checksum to use
	 * 
	 * @return {@link ChecksumEncodingEnum} used for the server
	 * @throws JargonException
	 */
	public abstract ChecksumEncodingEnum determineChecksumEncodingForTargetServer()
			throws JargonException;

}