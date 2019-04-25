/**
 * 
 */
package SOM_Strafford_PKG;

import base_Utils_Objects.messageObject;

/**
 * this class will launch the SOM project without any dependencies on any processing applets or code
 * @author john
 */
public class StraffordSOM {
	StraffSOMMapManager mapMgr;
	private messageObject msgObj;
	/**
	 * 
	 */
	public StraffordSOM(String[] args) {
		//dims should be large enough to make sure map nodes can be mapped to spaces - only used for displaying results and for placing map nodes in "locations" so that distance can be quickly computed
		float[] _dims = new float[] {834.8f,834.8f};
		
		//TODO _dirs need to be loaded from args
		String[] _dirs = new String[] {SOMProjConfigData._baseDir+SOMProjConfigData.configDir,SOMProjConfigData._baseDir};
		mapMgr = new StraffSOMMapManager(_dirs, _dims);
		msgObj = mapMgr.buildMsgObj();
	}
	
	public void test() {
		msgObj.dispInfoMessage("StraffordSOM","test",mapMgr.toString());
	
		
	}
	
	

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		StraffordSOM mainObj = new StraffordSOM(args);
		mainObj.test();

	}//main

}//StraffordSOM
