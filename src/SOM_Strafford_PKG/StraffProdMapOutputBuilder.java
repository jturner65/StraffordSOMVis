package SOM_Strafford_PKG;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Instances of this object will load a desired set of product IDs and compare them to the constructed SOM to find derive appropriate prospects.
 * Listings of these prospects will then be saved to disk
 * @author john
 *
 */
public class StraffProdMapOutputBuilder {
	public static final String fileComment = "#";
	public String fileName;
	public SOMMapManager mapMgr;
	private SOMProjConfigData projConfigData;
	private ProductExample[] prodsToMap;
	private String[] fullQualOutDirs;
	private final boolean isMT;
	private ExecutorService th_exec;
	private int prodDistType;
	private double prodZoneDistThresh;

	public StraffProdMapOutputBuilder(SOMMapManager _mapMgr, String _fileName, ExecutorService _th_exec, int _pDistType, double _pZnDistThresh) {
		mapMgr = _mapMgr;projConfigData = mapMgr.projConfigData;th_exec = _th_exec;
		prodDistType = _pDistType; prodZoneDistThresh = _pZnDistThresh;
		isMT = mapMgr.isMTCapable();
		loadConfigAndSetVars( _fileName);
	}//ctor
	
	private void loadConfigAndSetVars(String _fileName) {
		mapMgr.dispMessage("StraffProdMapOutputBuilder", "loadConfigAndSetVars","Start loading product-to-prospect mapping configurations.");
		fileName = _fileName;
		String[] configDatList = mapMgr.loadFileIntoStringAra(fileName, "Products-To-Map File Loaded", "Products-To-Map File Not Loaded Due To Error");
		int idx = 0;
		String[] strVals = new String[0];
		ArrayList<String> prodIDsToMapInit = new ArrayList<String>();
		//move past initial comments, if any exist
		for (int i=0; i<configDatList.length;++i) {
			if (configDatList[idx].contains(fileComment)) { continue;}//move past initial comments, if any exist
			strVals = configDatList[i].trim().split(",");
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
		ArrayList<ProductExample> prodsToMapLst = new ArrayList<ProductExample>();
		for (String pId : prodIDsToMapInit) {
			ProductExample ex = mapMgr.getProductByID(pId);
			if(ex == null) {mapMgr.dispMessage("StraffProdMapper", "loadConfigAndSetVars", "Error! Product ID : " + pId + " is not present in currently loaded product listings.  Ignoring this product.");}
			else {				prodsToMapLst.add(ex);			}
		}		
		
		prodsToMap = prodsToMapLst.toArray(new ProductExample[0]);
		fullQualOutDirs = new String[prodsToMap.length];
		mapMgr.dispMessage("StraffProdMapOutputBuilder", "loadConfigAndSetVars","Found "+prodsToMap.length+" products to aggregate prospect suggestions for.");
		String zoneDistThreshStr = String.format("_distThresh_%8f", prodZoneDistThresh);
		zoneDistThreshStr.replace('.', '-');
		String sfx = String.format("dTyp_%1d", prodDistType) + zoneDistThreshStr;
		
		String outDirBase = projConfigData.getFullProdOutMapperBaseDir(sfx);
		for (int i=0;i<prodsToMap.length;++i) {			fullQualOutDirs[i]=projConfigData.getPerProdOutSubDirName(outDirBase, prodsToMap[i].OID);}	
		mapMgr.dispMessage("StraffProdMapOutputBuilder", "loadConfigAndSetVars","Finished loading product-to-prospect mapping configurations.");
	}//loadConfigAndSetVars	
	
	//save mapping results either through multiple threads or in a single thread
	public void saveAllSpecifiedProdMappings() {//launch in either a single thread or multiples
		mapMgr.dispMessage("StraffProdMapOutputBuilder", "saveAllSpecifiedProdMappings", "Starting Saving Product data to file.");
		//all mappings are known by here - perhaps load balance?
		if(isMT) {//save multiple product mappings per thread
			int numUsableThreads = mapMgr.getNumUsableThreads();
			List<Future<Boolean>> prdcttMapperFtrs = new ArrayList<Future<Boolean>>();
			List<StraffProdOutMapper> prdcttMappers = new ArrayList<StraffProdOutMapper>();
			int numForEachThrd = ((int)((prodsToMap.length-1)/(1.0f*numUsableThreads))) + 1;
			//use this many for every thread but last one
			int stIDX = 0;
			int endIDX = numForEachThrd;				
			for (int i=0; i<(numUsableThreads-1);++i) {				
				prdcttMappers.add(new StraffProdOutMapper(mapMgr, stIDX, endIDX, i, prodDistType, prodZoneDistThresh, prodsToMap,fullQualOutDirs));
				stIDX = endIDX;
				endIDX += numForEachThrd;
			}
			//last one probably won't end at endIDX, so use length
			prdcttMappers.add(new StraffProdOutMapper(mapMgr,stIDX, prodsToMap.length, numUsableThreads-1, prodDistType, prodZoneDistThresh, prodsToMap,fullQualOutDirs));
			try {prdcttMapperFtrs = th_exec.invokeAll(prdcttMappers);for(Future<Boolean> f: prdcttMapperFtrs) { f.get(); }} catch (Exception e) { e.printStackTrace(); }	
			
		} else {//save every product in single thread
			for (int i=0;i<prodsToMap.length;++i) {
				ProductExample ex = prodsToMap[i];
				String[] strRes = ex.getAllExsStrAra(prodDistType,prodZoneDistThresh);
				String OID = ex.OID;
				String outDir = fullQualOutDirs[i];
				String fileNameToSave = outDir + OID +"_mappingResults.csv";
				mapMgr.saveStrings(fileNameToSave, strRes);
			}
		}
		mapMgr.dispMessage("StraffProdMapOutputBuilder", "saveAllSpecifiedProdMappings", "Starting Saving Product data to file.");
		
	}//mapAllSpecifiedProds
	

	
	//getPerProdOutSubDirName

}//class StraffProdMapOutputBuilder

class StraffProdOutMapper implements Callable<Boolean>{
	private SOMMapManager mapMgr;
	private ProductExample[] prodsToMap;
	private String[] fullQualOutDirs;
	private int stIDX, endIDX, thdIDX, prodDistType;
	private double prodZoneDistThresh;
	private SOMProjConfigData projConfigData;
	
	public StraffProdOutMapper(SOMMapManager _mapMgr, int _stIDX, int _endIDX, int _thdIDX, int _pDistType, double _pZnDistThresh, ProductExample[] _prodsToMap, String[] _fullQualOutDirs) {
		mapMgr = _mapMgr;		projConfigData = mapMgr.projConfigData;
		prodsToMap = _prodsToMap;	fullQualOutDirs = _fullQualOutDirs;	
		stIDX = _stIDX;		endIDX = _endIDX;		thdIDX = _thdIDX;		prodDistType = _pDistType; prodZoneDistThresh = _pZnDistThresh;
	}//ctor
	
	//write data to file
	public void saveStrings(String fname, String[] data) {
		PrintWriter pw = null;
		try {
		     File file = new File(fname);
		     FileWriter fw = new FileWriter(file, false);
		     pw = new PrintWriter(fw);
		     for (int i=0;i<data.length;++i) { pw.println(data[i]);}
		     
		} catch (IOException e) {	e.printStackTrace();}
		finally {			if (pw != null) {pw.close();}}
	}//saveStrings
	//outputDir 
	
	@Override
	public Boolean call() throws Exception {
		//save all products from stIDX to endIDX
		mapMgr.dispMessage("StraffProdOutMapper", "Run Thread : " +thdIDX, "Starting Saving Product mappings data to file from " +stIDX +" to "+ endIDX+".");
		for (int i=stIDX; i<endIDX;++i) {
			ProductExample ex = prodsToMap[i];
			String[] strRes = ex.getAllExsStrAra(prodDistType,prodZoneDistThresh);
			String OID = ex.OID;
			String outDir = fullQualOutDirs[i];
			String fileNameToSave = outDir + OID +"_mappingResults.csv";
			saveStrings(fileNameToSave, strRes);
		}		
		mapMgr.dispMessage("StraffProdOutMapper", "Run Thread : " +thdIDX, "Finished Product data to BMU mapping");	
		return true;
	}//call
	
	
}//StraffProdOutMapper