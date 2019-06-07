package strafford_SOM_PKG.straff_SOM_Mapping.outputMappers.base;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;

import base_SOM_Objects.som_examples.SOMExample;
import base_SOM_Objects.som_examples.SOMMapNode;
import base_SOM_Objects.som_utils.SOMProjConfigData;
import base_Utils_Objects.FileIOManager;
import base_Utils_Objects.MessageObject;
import base_Utils_Objects.MsgCodes;
import strafford_SOM_PKG.straff_SOM_Examples.products.ProductExample;
import strafford_SOM_PKG.straff_SOM_Mapping.Straff_SOMMapManager;
import strafford_SOM_PKG.straff_Utils.Straff_SOMProjConfig;

public abstract class Straff_ProdMappingOutputBuilder_Base {
	protected MessageObject msgObj;
	public static final String fileComment = "#";
	public String fileName;
	public Straff_SOMMapManager mapMgr;
	protected FileIOManager fileIO;
	protected SOMProjConfigData projConfigData;
	protected ProductExample[] prodsToMap;
	protected String[] fullQualOutPerProdDirs;
	protected String fullQualOutPerProspectDir;
	protected final boolean isMT;
	protected ExecutorService th_exec;
	protected final int numUsableThreads;
	protected final String callingClassName;
	
	public Straff_ProdMappingOutputBuilder_Base(Straff_SOMMapManager _mapMgr, String _fileName, ExecutorService _th_exec, String _className) {
		mapMgr = _mapMgr; msgObj = _mapMgr.buildMsgObj();
		numUsableThreads = mapMgr.getNumUsableThreads();
		fileIO = new FileIOManager(msgObj,"Straff_ProdOutputBuilderBase");
		projConfigData = mapMgr.projConfigData;th_exec = _th_exec;
		isMT = mapMgr.isMTCapable();
		callingClassName=_className;
		loadConfigAndSetVars( _fileName);
	}//ctor
	
	private final void loadConfigAndSetVars(String _fileName) {
		msgObj.dispMessage("Base->"+callingClassName, "loadConfigAndSetVars","Start loading product-to-prospect mapping configurations.", MsgCodes.info5);
		fileName = _fileName;
		String[] configDatList = fileIO.loadFileIntoStringAra(fileName, "Products-To-Map File Loaded", "Products-To-Map File Not Loaded Due To Error");
		String[] strVals = new String[0];
		ArrayList<String> prodIDsToMapInit = new ArrayList<String>();
		//move past initial comments, if any exist
		for (int i=0; i<configDatList.length;++i) {
			if (configDatList[i].contains(fileComment)) { continue;}//move past initial comments, if any exist
			strVals = configDatList[i].trim().split(mapMgr.csvFileToken);
			if(strVals.length == 1) {//1 entry per line
				String pId = strVals[0].trim();
				if(pId.length() != 0) {				prodIDsToMapInit.add(pId);}
			} else {//multiple entries per line - if present MUST BE SEPARATED BY COMMAS
				for (String val : strVals) {
					String pId = val.trim();
					if(pId.length() != 0) {				prodIDsToMapInit.add(pId);}
				}
			}
		}//for each line in config file	
		//verify prod ids are found in loaded productMap
		ArrayList<SOMExample> prodsToMapLst = new ArrayList<SOMExample>();
		for (String pId : prodIDsToMapInit) {
			SOMExample ex = mapMgr.getProductByID(pId);
			if(ex == null) {msgObj.dispMessage("Base->"+callingClassName, "loadConfigAndSetVars", "Error! Product ID : " + pId + " is not present in currently loaded product listings.  Ignoring this product.", MsgCodes.warning2);}
			else {				prodsToMapLst.add(ex);			}
		}				
		prodsToMap = prodsToMapLst.toArray(new ProductExample[0]);
		fullQualOutPerProdDirs = new String[prodsToMap.length];
		msgObj.dispMessage("Base->"+callingClassName, "loadConfigAndSetVars","Found "+prodsToMap.length+" products to aggregate prospect suggestions for.", MsgCodes.info1);
		
		fullQualOutPerProspectDir = buildFullQualOutPerProspectDir();
		
		for (int i=0;i<prodsToMap.length;++i) {			fullQualOutPerProdDirs[i]=((Straff_SOMProjConfig)projConfigData).getPerProdOutSubDirName(fullQualOutPerProspectDir, prodsToMap[i].OID);}	
		msgObj.dispMessage("Base->"+callingClassName, "loadConfigAndSetVars","Finished loading product-to-prospect mapping configurations.", MsgCodes.info5);
	}//loadConfigAndSetVars		
	//save mapping results either through multiple threads or in a single thread
	protected abstract String buildFullQualOutPerProspectDir();
	
	//get # of elements to map per thread for multi-threaded exec
	protected final int getNumToMapPerThd(int numExTtl) {return ((int)((numExTtl-1)/(1.0f*numUsableThreads))) + 1;}	
	
	//////////////////
	// save product mapping
	
	public final void saveAllSpecifiedProdMappings() {//launch in either a single thread or multiples
		msgObj.dispMessage("Base->"+callingClassName, "saveAllSpecifiedProdMappings", "Starting Saving " +prodsToMap.length + " Product data to file.", MsgCodes.info5);
		//all mappings are known by here - perhaps load balance?
		if((isMT) && (prodsToMap.length >= 2*numUsableThreads)) {//save multiple product mappings per thread
			saveAllSpecifiedProdMappings_MT(getNumToMapPerThd(prodsToMap.length));		
		} else {//save every product in single thread
			saveAllSpecifiedProdMappings_ST();
		}
		msgObj.dispMessage("Base->"+callingClassName, "saveAllSpecifiedProdMappings", "Finished Saving Product data to file.", MsgCodes.info5);		
	}//mapAllSpecifiedProds	
	protected abstract void saveAllSpecifiedProdMappings_MT(int numForEachThrd);
	protected abstract void saveAllSpecifiedProdMappings_ST();	
	
	//////////////////////////////
	// save prospect mappings
	public final void saveAllProspectToProdMappings(SOMExample[] prospectsToMap, String typeOfProspect) {
		if(prospectsToMap.length == 0) {
			msgObj.dispMessage("Base->"+callingClassName, "saveAllProspectToProdMappings", "No prospects of type : " + typeOfProspect + " to save mapping for.  Aborting.", MsgCodes.warning2);
			return;
		}
		msgObj.dispMessage("Base->"+callingClassName, "saveAllProspectToProdMappings", "Starting Saving " + typeOfProspect + " Prospects to Product mappings to file.", MsgCodes.info5);
		HashMap<ProductExample, HashMap<SOMMapNode, Double>> prodToMapNodes = getProdsToMapNodes();

		//all mappings are known by here - perhaps load balance?
		if(isMT) {//save multiple product mappings per thread
			saveAllSpecifiedProspectMappings_MT(prospectsToMap, typeOfProspect, prodToMapNodes, getNumToMapPerThd(prospectsToMap.length));			
		} else {//save prospect 	
			saveAllSpecifiedProspectMappings_ST(prospectsToMap, typeOfProspect, prodToMapNodes);		
		}
		msgObj.dispMessage("Base->"+callingClassName, "saveAllProspectToProdMappings", "Finished Saving " + typeOfProspect + " Prospect to Product data to file.", MsgCodes.info5);
		
	}//saveAllProspectToProdMappings	
	//return mapping of products to map
	protected abstract HashMap<ProductExample, HashMap<SOMMapNode, Double>> getProdsToMapNodes();	
	protected abstract void saveAllSpecifiedProspectMappings_MT(SOMExample[] prospectsToMap, String typeOfProspect, HashMap<ProductExample, HashMap<SOMMapNode, Double>> prods, int numForEachThrd);
	protected abstract void saveAllSpecifiedProspectMappings_ST(SOMExample[] prospectsToMap, String typeOfProspect, HashMap<ProductExample, HashMap<SOMMapNode, Double>> prods);


}//Straff_ProdOutputBuilderBase
