package examples;


import java.util.Iterator;
import java.util.List;

import org.irods.jargon.core.connection.IRODSAccount;
import org.irods.jargon.core.pub.CollectionAndDataObjectListAndSearchAO;
import org.irods.jargon.core.pub.IRODSAccessObjectFactory;
import org.irods.jargon.core.pub.IRODSFileSystem;
import org.irods.jargon.core.pub.UserAO;
import org.irods.jargon.core.pub.io.IRODSFileFactory;
import org.irods.jargon.core.query.CollectionAndDataObjectListingEntry;

public class iRODSList {

	public static void main(String[] args) throws Exception {
	    String host = args[0];
	    int port = Integer.parseInt(args[1]);
	    String loginuser = args[2];
	    String passwd = args[3];
	    String homedir = args[4];
	    String zoneName = args[5];
	    String defaultStorageResc = args[6];
	    String targetIrodsCollection = "/ncsazone/home/rods/10/";
	    IRODSAccount irodsAccount = new IRODSAccount(host, port, loginuser, passwd, homedir, zoneName, defaultStorageResc);
	    System.out.println(irodsAccount);
	    
	    IRODSFileSystem irodsFileSystem = IRODSFileSystem.instance();
	    
	    IRODSAccessObjectFactory irodsAccessObjectFactory = irodsFileSystem.getIRODSAccessObjectFactory();
	    
	    IRODSFileFactory irodsFileFactory = irodsAccessObjectFactory.getIRODSFileFactory(irodsAccount);
	    
	    UserAO userAO = irodsAccessObjectFactory.getUserAO(irodsAccount);
	    
	    CollectionAndDataObjectListAndSearchAO actualCollection = irodsAccessObjectFactory.getCollectionAndDataObjectListAndSearchAO(irodsAccount);
	    
	    List<CollectionAndDataObjectListingEntry> entries = null;
        try {
        	//entries = actualCollection.listDataObjectsAndCollectionsUnderPath(targetIrodsCollection);
        	entries = actualCollection.listDataObjectsAndCollectionsUnderPathWithPermissions(targetIrodsCollection);
        } catch (org.irods.jargon.core.exception.FileNotFoundException e1) {
            e1.printStackTrace();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
	    
        Iterator<CollectionAndDataObjectListingEntry> datacursor = entries.iterator();
        while(datacursor.hasNext()){
        	CollectionAndDataObjectListingEntry entry = datacursor.next();
        	//System.out.println("type: " + entry.getObjectType());
        	switch (entry.getObjectType()){
        	case DATA_OBJECT:
        		System.out.println("file");
        		break;
        	case COLLECTION:
        		System.out.println("folder");
        		break;
        	}
        	System.out.println("\tname: " + entry.getNodeLabelDisplayValue());
        	System.out.println("\tsize: " + entry.getDataSize());
        	System.out.println("\towner: " + entry.getOwnerName());
        	System.out.println("\tCtime: " + entry.getCreatedAt());
        	System.out.println("\tMtime: " + entry.getModifiedAt());
        	System.out.println("\tabs path: " + entry.getFormattedAbsolutePath());
        }
	    
	    System.out.println("done");
	}

}
