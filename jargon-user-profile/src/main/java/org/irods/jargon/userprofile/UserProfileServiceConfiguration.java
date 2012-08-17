/**
 * 
 */
package org.irods.jargon.userprofile;

/**
 * Configuration settings for the user profile service. Controls behavior and
 * defaults of the user profile service
 * 
 * @author Mike Conway - DICE (www.irods.org)
 * 
 */
public class UserProfileServiceConfiguration {

	public static final String PUBLIC_PROFILE_FILE_NAME = ".profile";
	public static final String PROTECTED_PROFILE_FILE_NAME = ".protected_profile";

	private String publicProfileFileName = PUBLIC_PROFILE_FILE_NAME;
	private String protectedProfileFileName = PROTECTED_PROFILE_FILE_NAME;
	private String protectedProfileReadWriteGroup = "protected_profile_read_group";
	private boolean protectedProfileGroupHasWriteAccessToPublic = true;

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("userProfileServiceConfiguration");
		sb.append("\n    publicProfileFileName:");
		sb.append(publicProfileFileName);
		sb.append("\n    protectedProfileReadWriteGroup:");
		sb.append(protectedProfileReadWriteGroup);
		sb.append("\n    protectedProfileGroupHasWriteAccessToPublic:");
		sb.append(protectedProfileGroupHasWriteAccessToPublic);
		return sb.toString();
	}

	/**
	 * @return the publicProfileFileName which is the file name of the public
	 *         profile
	 */
	public String getPublicProfileFileName() {
		return publicProfileFileName;
	}

	/**
	 * @param publicProfileFileName
	 *            the publicProfileFileName to set which is the file name of the
	 *            public profile
	 */
	public void setPublicProfileFileName(String publicProfileFileName) {
		this.publicProfileFileName = publicProfileFileName;
	}

	/**
	 * @return the protectedProfileFileName the name of the protected profile
	 *         file
	 */
	public String getProtectedProfileFileName() {
		return protectedProfileFileName;
	}

	/**
	 * @param protectedProfileFileName
	 *            the protectedProfileFileName to set
	 */
	public void setProtectedProfileFileName(String protectedProfileFileName) {
		this.protectedProfileFileName = protectedProfileFileName;
	}

	/**
	 * @return the protectedProfileReadWriteGroup the iRODS user group that will
	 *         be given read/write access to the protected part of the profile
	 */
	public String getProtectedProfileReadWriteGroup() {
		return protectedProfileReadWriteGroup;
	}

	/**
	 * @param protectedProfileReadWriteGroup
	 *            the protectedProfileReadWriteGroup to set the iRODS user group
	 *            that will be given read/write access to the protected part of
	 *            the profile
	 */
	public void setProtectedProfileReadWriteGroup(
			String protectedProfileReadWriteGroup) {
		this.protectedProfileReadWriteGroup = protectedProfileReadWriteGroup;
	}

	/**
	 * @return the protectedProfileGroupHasWriteAccessToPublic indicates whether
	 *         the protected profile read/write group has write access to the
	 *         public part of the profile
	 */
	public boolean isProtectedProfileGroupHasWriteAccessToPublic() {
		return protectedProfileGroupHasWriteAccessToPublic;
	}

	/**
	 * @param protectedProfileGroupHasWriteAccessToPublic
	 *            the protectedProfileGroupHasWriteAccessToPublic to set
	 */
	public void setProtectedProfileGroupHasWriteAccessToPublic(
			boolean protectedProfileGroupHasWriteAccessToPublic) {
		this.protectedProfileGroupHasWriteAccessToPublic = protectedProfileGroupHasWriteAccessToPublic;
	}
}
