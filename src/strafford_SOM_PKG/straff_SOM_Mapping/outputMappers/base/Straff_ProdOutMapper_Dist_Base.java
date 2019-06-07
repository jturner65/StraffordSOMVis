package strafford_SOM_PKG.straff_SOM_Mapping.outputMappers.base;

import java.util.ArrayList;
import java.util.concurrent.Callable;

import base_SOM_Objects.som_utils.SOMProjConfigData;
import base_Utils_Objects.FileIOManager;
import base_Utils_Objects.MessageObject;
import base_Utils_Objects.MsgCodes;
import strafford_SOM_PKG.straff_SOM_Examples.products.ProductExample;
import strafford_SOM_PKG.straff_SOM_Mapping.Straff_SOMMapManager;

public abstract class Straff_ProdOutMapper_Dist_Base implements Callable<Boolean>{
	protected MessageObject msgObj;
	protected FileIOManager fileIO;
	protected ProductExample[] prodsToMap;
	protected String[] fullQualOutDirs;
	protected int stIDX, endIDX, thdIDX;
	protected SOMProjConfigData projConfigData;

	public Straff_ProdOutMapper_Dist_Base(Straff_SOMMapManager _mapMgr, int _stIDX, int _endIDX, int _thdIDX, ProductExample[] _prodsToMap, String[] _fullQualOutDirs) {
		msgObj = _mapMgr.buildMsgObj();		projConfigData = _mapMgr.projConfigData;
		prodsToMap = _prodsToMap;	fullQualOutDirs = _fullQualOutDirs;	
		stIDX = _stIDX;		endIDX = _endIDX;		thdIDX = _thdIDX;		
		fileIO = new FileIOManager(msgObj,"StraffProdOutMapper_"+thdIDX);
		
	}
	/**
	 * instance-specific code to get each example's list of output
	 */
	protected abstract ArrayList<String> getPerExampleDataAra(ProductExample ex);
	
	@Override
	public final Boolean call() {
		msgObj.dispMessage("Straff_ProdOutMapper_Dist_Base", "Run Thread : " +thdIDX, "Starting Saving Product mappings data to file from " +stIDX +" to "+ endIDX+".", MsgCodes.info5);
		for (int i=stIDX; i<endIDX;++i) {
			ProductExample ex = prodsToMap[i];
			ArrayList<String> strRes = getPerExampleDataAra(ex);
			String OID = ex.OID;
			String outDir = fullQualOutDirs[i];
			String fileNameToSave = outDir + OID +"_mappingResults.csv";
			fileIO.saveStrings(fileNameToSave, strRes);
		}		
		msgObj.dispMessage("Straff_ProdOutMapper_Dist_Base", "Run Thread : " +thdIDX, "Finished Saving Product mappings data to file from " +stIDX +" to "+ endIDX+".", MsgCodes.info5);
		return true;
	}
}//class Straff_ProdOutMapper_Dist_Base
