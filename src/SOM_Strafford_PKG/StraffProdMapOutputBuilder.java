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
	private String[] fullQualOutPerProdDirs;
	private String fullQualOutPerProspectDir;
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
		fullQualOutPerProdDirs = new String[prodsToMap.length];
		mapMgr.dispMessage("StraffProdMapOutputBuilder", "loadConfigAndSetVars","Found "+prodsToMap.length+" products to aggregate prospect suggestions for.");
		String zoneDistThreshStr = String.format("_distThresh_%8f", prodZoneDistThresh);
		zoneDistThreshStr.replace('.', '-');
		String sfx = String.format("dTyp_%1d", prodDistType) + zoneDistThreshStr;
		
		fullQualOutPerProspectDir = projConfigData.getFullProdOutMapperBaseDir(sfx);
		for (int i=0;i<prodsToMap.length;++i) {			fullQualOutPerProdDirs[i]=projConfigData.getPerProdOutSubDirName(fullQualOutPerProspectDir, prodsToMap[i].OID);}	
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
				prdcttMappers.add(new StraffProdOutMapper(mapMgr, stIDX, endIDX, i, prodDistType, prodZoneDistThresh, prodsToMap,fullQualOutPerProdDirs));
				stIDX = endIDX;
				endIDX += numForEachThrd;
			}
			//last one probably won't end at endIDX, so use length
			prdcttMappers.add(new StraffProdOutMapper(mapMgr,stIDX, prodsToMap.length, numUsableThreads-1, prodDistType, prodZoneDistThresh, prodsToMap,fullQualOutPerProdDirs));
			try {prdcttMapperFtrs = th_exec.invokeAll(prdcttMappers);for(Future<Boolean> f: prdcttMapperFtrs) { f.get(); }} catch (Exception e) { e.printStackTrace(); }	
			
		} else {//save every product in single thread
			for (int i=0;i<prodsToMap.length;++i) {
				ProductExample ex = prodsToMap[i];
				String[] strRes = ex.getAllExmplsPerProdStrAra(prodDistType,prodZoneDistThresh);
				String OID = ex.OID;
				String outDir = fullQualOutPerProdDirs[i];
				String fileNameToSave = outDir + OID +"_mappingResults.csv";
				mapMgr.saveStrings(fileNameToSave, strRes);
			}
		}
		mapMgr.dispMessage("StraffProdMapOutputBuilder", "saveAllSpecifiedProdMappings", "Finished Saving Product data to file.");		
	}//mapAllSpecifiedProds
	
	
	private void _saveStrAraToFile(ArrayList<String> strList, String hdrStr, String fileTypeName) {
		//now split into partitions and save
		ArrayList<ArrayList<String>> csvRes = new ArrayList<ArrayList<String>>();
		ArrayList<String> csvResTmp = new ArrayList<String>();		
		int numProspectsPerFile = strList.size()/10;
		int counter = 0;
		//String hdrStr = "Prospect OID,Prospect BMU Dist,Product OID, Product Confidence,...";
		csvResTmp.add( hdrStr);
		int nameCounter = 0;
		for (String s : strList) {			
			csvResTmp.add(s);
			++counter;
			if(counter % numProspectsPerFile ==0) {
				mapMgr.dispMessage("StraffProdMapOutputBuilder","saveAllProspectToProdMappings","Done Building String Array : " +(nameCounter++));
				counter = 0;
				csvRes.add(csvResTmp); 
				csvResTmp = new ArrayList<String>();
				csvResTmp.add( hdrStr);
			}
		}
		csvRes.add(csvResTmp);
		//save array of arrays of strings, partitioned and named so that no file is too large
		nameCounter = 0;
		for (ArrayList<String> csvResSubAra : csvRes) {		
			mapMgr.dispMessage("StraffProdMapOutputBuilder","saveAllProspectToProdMappings","Saving Per-Prospect Product Mapping suggestions : " +nameCounter);
			mapMgr.saveStrings(fullQualOutPerProspectDir + "Prospect_mappingResults_" + nameCounter+".csv", csvResSubAra);
			++nameCounter;
		}

	}
	
	//pass input data array?
	public void saveAllProspectToProdMappings(ProspectExample[] prospectsToMap) {
		mapMgr.dispMessage("StraffProdMapOutputBuilder", "saveAllProspectToProdMappings", "Starting Saving Prospect to Product data to file.");
		HashMap<ProductExample, HashMap<SOMMapNode, Double>> prodToMapNodes = new HashMap<ProductExample, HashMap<SOMMapNode, Double>>();
		//build for every product to be mapped, HashMap keyed by map node that holds confidences that are within specified distances from product, using specified distance measure
		for (ProductExample ex : prodsToMap) {		//map result below contains map nodes that have >0 confidence for product ex
			HashMap<SOMMapNode, Double> resForAllMapNodes = ex.getMapNodeConf(prodDistType, prodZoneDistThresh);
			prodToMapNodes.put(ex, resForAllMapNodes);
		}
		
		//all mappings are known by here - perhaps load balance?
		if(isMT) {//save multiple product mappings per thread
			int numUsableThreads = mapMgr.getNumUsableThreads();
			List<Future<Boolean>> prdcttMapperFtrs = new ArrayList<Future<Boolean>>();
			List<StraffProspectOutMapper> prdcttMappers = new ArrayList<StraffProspectOutMapper>();
			int numForEachThrd = ((int)((prospectsToMap.length-1)/(1.0f*numUsableThreads))) + 1;
			//use this many for every thread but last one
			int stIDX = 0;
			int endIDX = numForEachThrd;				
			for (int i=0; i<(numUsableThreads-1);++i) {		
				prdcttMappers.add(new StraffProspectOutMapper(mapMgr, stIDX, endIDX, i, prospectsToMap, prodToMapNodes, fullQualOutPerProspectDir));
				stIDX = endIDX;
				endIDX += numForEachThrd;
			}
			//last one probably won't end at endIDX, so use length
			prdcttMappers.add(new StraffProspectOutMapper(mapMgr,stIDX, prospectsToMap.length, numUsableThreads-1,prospectsToMap, prodToMapNodes, fullQualOutPerProspectDir));
			try {prdcttMapperFtrs = th_exec.invokeAll(prdcttMappers);for(Future<Boolean> f: prdcttMapperFtrs) { f.get(); }} catch (Exception e) { e.printStackTrace(); }	
			
		} else {//save prospect 			 
			ArrayList<String> strList = new ArrayList<String>(),strListNoMaps = new ArrayList<String>();		
			TreeMap<Double, String> resCalcData = new TreeMap<Double, String> (new Comparator<Double>() { @Override public int compare(Double o1, Double o2) {   return o2.compareTo(o1);}});//descending key order
			String outRes;
			for (int i=0; i<prospectsToMap.length;++i) {
				ProspectExample ex = prospectsToMap[i];
				String exOutStr = ""+ex.OID + ","+String.format("%.6f",ex._sqDistToBMU)+",";
				resCalcData.clear();
				for (ProductExample prod : prodToMapNodes.keySet()) {
					HashMap<SOMMapNode, Double> nodeConfsToProds = prodToMapNodes.get(prod);					
					Double conf = nodeConfsToProds.get(ex.bmu);//now find confidence of prod in this node's bmu
					if(conf == 0.0) {continue;}
					outRes = resCalcData.get(conf);
					if(outRes == null) {outRes = "";}
					outRes +=""+prod.OID+":"+String.format("%.6f",conf)+",";
					resCalcData.put(conf, outRes);
				}
			
				if (resCalcData.size() > 0) {				
					for(Double conf : resCalcData.keySet()){		exOutStr += resCalcData.get(conf);}
					strList.add(exOutStr);
				} else {
					exOutStr += "No Mappings With Chosen Products";
					strListNoMaps.add(exOutStr);
				}
			}//for every prospect			
			String hdrStr = "Prospect OID,Prospect BMU Dist,Product OID, Product Confidence,...";
			_saveStrAraToFile(strList, hdrStr, "Prospect_mappingResults_");
			hdrStr = "Prospect OID,Prospect BMU Dist,<these prospects have no product mappings among specified products>";
			_saveStrAraToFile(strListNoMaps, hdrStr, "Prospect_WithoutMappings_");
			
		}
		mapMgr.dispMessage("StraffProdMapOutputBuilder", "saveAllProspectToProdMappings", "Finished Saving Prospect to Product data to file.");
		
	}//saveAllProspectToProdMappings
	

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
			String[] strRes = ex.getAllExmplsPerProdStrAra(prodDistType,prodZoneDistThresh);
			String OID = ex.OID;
			String outDir = fullQualOutDirs[i];
			String fileNameToSave = outDir + OID +"_mappingResults.csv";
			saveStrings(fileNameToSave, strRes);
		}		
		mapMgr.dispMessage("StraffProdOutMapper", "Run Thread : " +thdIDX, "Finished Product data to BMU mapping");	
		return true;
	}//call
}//StraffProdOutMapper

class StraffProspectOutMapper implements Callable<Boolean>{
	private SOMMapManager mapMgr;
	private ProspectExample[] prospectsToMap;
	private int stIDX, endIDX, thdIDX;
	private HashMap<ProductExample, HashMap<SOMMapNode, Double>> prodToMapNodes;
	private String fileNameToSave, fileNameBadProspectsToSave;
	TreeMap<Double, String> resCalcData;		//declare here to hopefully speed up calc
	public StraffProspectOutMapper(SOMMapManager _mapMgr, int _stIDX, int _endIDX, int _thdIDX,  ProspectExample[] _prospectsToMap, HashMap<ProductExample, HashMap<SOMMapNode, Double>> _prodToMapNodes,String _fullQualOutDir) {
		mapMgr = _mapMgr;		
		stIDX = _stIDX;		endIDX = _endIDX;		thdIDX = _thdIDX;	
		prospectsToMap = _prospectsToMap;	
		prodToMapNodes = _prodToMapNodes;
		resCalcData = new TreeMap<Double, String> (new Comparator<Double>() { @Override public int compare(Double o1, Double o2) {   return o2.compareTo(o1);}});//descending key order
		fileNameToSave = _fullQualOutDir + "Prospect_mappingResults_" + thdIDX+".csv";
		fileNameBadProspectsToSave = _fullQualOutDir + "Prospect_WithoutMappings_" + thdIDX+".csv";
	}//ctor
	
	//write data to file
	public void saveStrings(String fname, ArrayList<String> data) {
		PrintWriter pw = null;
		try {
		     File file = new File(fname);
		     FileWriter fw = new FileWriter(file, false);
		     pw = new PrintWriter(fw);
		     for (int i=0;i<data.size();++i) { pw.println(data.get(i));}
		     
		} catch (IOException e) {	e.printStackTrace();}
		finally {			if (pw != null) {pw.close();}}
	}//saveStrings
	//outputDir 
	
	@Override
	public Boolean call() throws Exception {
		//save all products from stIDX to endIDX
		mapMgr.dispMessage("StraffProspectOutMapper", "Run Thread : " +thdIDX, "Starting Prospect data to Product data to file from prospect IDs " +stIDX +" to "+ endIDX+".");
		ArrayList<String> strList = new ArrayList<String>(), strListNoMaps = new ArrayList<String>();	
		strList.add("Prospect OID,Prospect BMU Dist,Product OID, Product Confidence,...");
		strListNoMaps.add("Prospect OID,Prospect BMU Dist,<these prospects have no product mappings among specified products>");
		String outRes;
		for (int i=stIDX; i<endIDX;++i) {
			ProspectExample ex = prospectsToMap[i];
			resCalcData.clear();
			String exOutStr = ""+ex.OID + ","+String.format("%.6f",ex._sqDistToBMU)+",";
			for (ProductExample prod : prodToMapNodes.keySet()) {//for every product
				HashMap<SOMMapNode, Double> nodeConfsToProds = prodToMapNodes.get(prod);				
				Double conf = nodeConfsToProds.get(ex.bmu);//now find confidence of prod in this node's bmu
				if((conf == null) || (conf == 0.0)) {continue;}
				outRes = resCalcData.get(conf);
				if(outRes == null) {outRes = "";}
				outRes +=""+prod.OID+":"+String.format("%.6f",conf)+",";
				resCalcData.put(conf, outRes);
			}
			if (resCalcData.size() > 0) {				
				for(Double conf : resCalcData.keySet()){		exOutStr += resCalcData.get(conf);}
				strList.add(exOutStr);
			} else {
				exOutStr += "No Mappings With Chosen Products";
				strListNoMaps.add(exOutStr);
			}
		}		
		mapMgr.dispMessage("StraffProdMapOutputBuilder","saveAllProspectToProdMappings","Saving Per-Prospect Product Mapping suggestions : " +thdIDX);
		saveStrings(fileNameToSave, strList);
		saveStrings(fileNameBadProspectsToSave, strListNoMaps);
		mapMgr.dispMessage("StraffProspectOutMapper", "Run Thread : " +thdIDX, "Finished Prospect data to Product BMU mapping");	
		return true;
	}//call
}//StraffProspectOutMapper