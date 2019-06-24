package strafford_SOM_PKG.straff_SOM_Mapping.outputMappers;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import base_SOM_Objects.som_examples.*;
import base_SOM_Objects.som_utils.SOMProjConfigData;
import base_Utils_Objects.*;
import base_Utils_Objects.io.MessageObject;
import strafford_SOM_PKG.straff_SOM_Examples.products.ProductExample;
import strafford_SOM_PKG.straff_SOM_Mapping.Straff_SOMMapManager;
import strafford_SOM_PKG.straff_SOM_Mapping.outputMappers.base.Straff_ProdMappingOutputBuilder_Base;
import strafford_SOM_PKG.straff_SOM_Mapping.outputMappers.base.Straff_ProdOutMapper_Dist_Base;
import strafford_SOM_PKG.straff_SOM_Mapping.outputMappers.base.Straff_ProspectOutMapper_Dist_Base;
import strafford_SOM_PKG.straff_Utils.Straff_SOMProjConfig;

/**
 * Instances of this object will load a desired set of product IDs and compare them to the constructed SOM to derive appropriate prospects, based on ftr-based bmu comparisons
 * Listings of these prospects will then be saved to disk
 * @author john
 *
 */
public class Straff_ProdMapOutBldr_FtrsAndBMUs extends Straff_ProdMappingOutputBuilder_Base {

	protected int prodDistType;
	protected double prodZoneDistThresh;

	public Straff_ProdMapOutBldr_FtrsAndBMUs(Straff_SOMMapManager _mapMgr, String _fileName, ExecutorService _th_exec, int _pDistType, double _pZnDistThresh) {
		super(_mapMgr,  _fileName,  _th_exec, "Straff_ProdMappingOutputBuilder_FtrsAndBMUs");
		prodDistType = _pDistType; 
		prodZoneDistThresh = _pZnDistThresh;

	}//ctor

	//save mapping results either through multiple threads or in a single thread
	protected String buildFullQualOutPerProspectDir() {
		String zoneDistThreshStr = String.format("_distThresh_%8f", prodZoneDistThresh);
		zoneDistThreshStr.replace('.', '-');
		String sfx = String.format("dTyp_%1d", prodDistType) + zoneDistThreshStr;		
		return((Straff_SOMProjConfig)projConfigData).getFullProdOutMapperBaseDir(sfx);
	}
	@Override
	protected void saveAllSpecifiedProdMappings_MT(int numForEachThrd) {		
		List<Future<Boolean>> prdcttMapperFtrs = new ArrayList<Future<Boolean>>();
		List<Straff_ProdOutMapper_Dist_FtrsBMUs> prdcttMappers = new ArrayList<Straff_ProdOutMapper_Dist_FtrsBMUs>();
		//use this many for every thread but last one
		int stIDX = 0;
		int endIDX = numForEachThrd;				
		for (int i=0; i<(numUsableThreads-1);++i) {				
			prdcttMappers.add(new Straff_ProdOutMapper_Dist_FtrsBMUs(mapMgr, stIDX, endIDX, i, prodDistType, prodZoneDistThresh, prodsToMap,fullQualOutPerProdDirs));
			stIDX = endIDX;
			endIDX += numForEachThrd;
		}
		//last one probably won't end at endIDX, so use length
		prdcttMappers.add(new Straff_ProdOutMapper_Dist_FtrsBMUs(mapMgr,stIDX, prodsToMap.length, numUsableThreads-1, prodDistType, prodZoneDistThresh, prodsToMap,fullQualOutPerProdDirs));
		try {prdcttMapperFtrs = th_exec.invokeAll(prdcttMappers);for(Future<Boolean> f: prdcttMapperFtrs) { f.get(); }} catch (Exception e) { e.printStackTrace(); }	
	}//saveAllSpecifiedProdMappings_MT
	@Override
	protected void saveAllSpecifiedProdMappings_ST() {
		//invoke single-threaded version synchronously
		Straff_ProdOutMapper_Dist_FtrsBMUs mapper = new Straff_ProdOutMapper_Dist_FtrsBMUs(mapMgr,0, prodsToMap.length, 0, prodDistType, prodZoneDistThresh, prodsToMap,fullQualOutPerProdDirs);
		mapper.call();
	}//saveAllSpecifiedProdMappings_ST()
	
	@Override
	protected HashMap<ProductExample, HashMap<SOM_MapNode, Double>> getProdsToMapNodes(){
		HashMap<ProductExample, HashMap<SOM_MapNode, Double>> prodToMapNodes = new HashMap<ProductExample, HashMap<SOM_MapNode, Double>>();
		//build for every product to be mapped, HashMap keyed by map node that holds confidences that are within specified distances from product, using specified distance measure
		for (ProductExample ex : prodsToMap) {		//map result below contains map nodes that have >0 confidence for product ex
			HashMap<SOM_MapNode, Double> resForAllMapNodes = ex.getMapNodeConf(prodDistType, prodZoneDistThresh);
			prodToMapNodes.put(ex, resForAllMapNodes);
		}
		return prodToMapNodes;
	}
	@Override
	protected void saveAllSpecifiedProspectMappings_MT(SOM_Example[] prospectsToMap, String typeOfProspect, HashMap<ProductExample, HashMap<SOM_MapNode, Double>> prodToMapNodes, int numForEachThrd) {
		List<Future<Boolean>> prdcttMapperFtrs = new ArrayList<Future<Boolean>>();
		List<Straff_ProspectOutMapper_Dist_FtrsBMUs> prdcttMappers = new ArrayList<Straff_ProspectOutMapper_Dist_FtrsBMUs>();
		//use this many for every thread but last one
		int stIDX = 0;
		int endIDX = numForEachThrd;				
		for (int i=0; i<(numUsableThreads-1);++i) {		
			prdcttMappers.add(new Straff_ProspectOutMapper_Dist_FtrsBMUs(msgObj, stIDX, endIDX, i, typeOfProspect, prospectsToMap, prodToMapNodes, fullQualOutPerProspectDir));
			stIDX = endIDX;
			endIDX += numForEachThrd;
		}
		//last one probably won't end at endIDX, so use length
		prdcttMappers.add(new Straff_ProspectOutMapper_Dist_FtrsBMUs(msgObj,stIDX, prospectsToMap.length, numUsableThreads-1, typeOfProspect, prospectsToMap, prodToMapNodes, fullQualOutPerProspectDir));
		try {prdcttMapperFtrs = th_exec.invokeAll(prdcttMappers);for(Future<Boolean> f: prdcttMapperFtrs) { f.get(); }} catch (Exception e) { e.printStackTrace(); }	
		
	};
	@Override
	protected void saveAllSpecifiedProspectMappings_ST(SOM_Example[] prospectsToMap, String typeOfProspect, HashMap<ProductExample, HashMap<SOM_MapNode, Double>> prodToMapNodes) {
		//invoke single-threaded version synchronously
		Straff_ProspectOutMapper_Dist_FtrsBMUs mapper = new Straff_ProspectOutMapper_Dist_FtrsBMUs(msgObj,0, prospectsToMap.length, 0, typeOfProspect, prospectsToMap, prodToMapNodes, fullQualOutPerProspectDir);
		mapper.call();//			
	}

}//class StraffProdMapOutputBuilder


/////////////////////////
// distance-to-BMU-based mappers
class Straff_ProdOutMapper_Dist_FtrsBMUs extends Straff_ProdOutMapper_Dist_Base{
	protected int prodDistType;
	protected double prodZoneDistThresh;
	
	public Straff_ProdOutMapper_Dist_FtrsBMUs(Straff_SOMMapManager _mapMgr, int _stIDX, int _endIDX, int _thdIDX, int _pDistType, double _pZnDistThresh, ProductExample[] _prodsToMap, String[] _fullQualOutDirs) {
		super( _mapMgr, _stIDX,_endIDX, _thdIDX, _prodsToMap, _fullQualOutDirs);
		prodDistType = _pDistType; prodZoneDistThresh = _pZnDistThresh;
	}//ctor
	
	/**
	 * instance-specific code to get each example's list of output
	 */
	protected ArrayList<String> getPerExampleDataAra(ProductExample ex){	return ex.getAllExmplsPerProdStrAra(prodDistType,prodZoneDistThresh);}

}//StraffProdOutMapper

class Straff_ProspectOutMapper_Dist_FtrsBMUs extends Straff_ProspectOutMapper_Dist_Base{	
	protected TreeMap<Double, String> resCalcData;		//declare here to hopefully speed up calc
	
	public Straff_ProspectOutMapper_Dist_FtrsBMUs(MessageObject _msgObj, int _stIDX, int _endIDX, int _thdIDX, String _prspctType,  SOM_Example[] _prospectsToMap, HashMap<ProductExample, HashMap<SOM_MapNode, Double>> _prodToMapNodes,String _fullQualOutDir) {
		super( _msgObj, _stIDX,_endIDX, _thdIDX, _prspctType, _prospectsToMap, _prodToMapNodes, _fullQualOutDir, "Straff_ProdOutMapper_Dist_FtrsBMUs");
		resCalcData = new TreeMap<Double, String> (new Comparator<Double>() { @Override public int compare(Double o1, Double o2) {   return o2.compareTo(o1);}});//descending key order
	}//ctor
	
	/**
	 * need to set 	outHdrStrHasMappings, outHdrStrHasNoMappings, used as headers for output files
	 */	
	@Override
	protected void setHdrStrings() {		
		outHdrStrHasMappings = "Prospect OID,Prospect BMU Dist,Product OID, Product Confidence,...";
		outHdrStrHasNoMappings = "Prospect OID,Prospect BMU Dist,<these prospects have no product mappings among specified products>";
	}
	
	@Override
	protected String getFileNameToSave(String _fullQualOutDir, String _prspctType) {return _fullQualOutDir + _prspctType + "_ftrbmu_mappingResults_" + thdIDX+".csv";}

	/**
	 * build list of output strings for each example
	 * @param strList array of strings for examples with mappings
	 * @param strListNoMaps array of strings of examples without mappings
	 * @param ex current example to map
	 */
	@Override
	protected void buildOutputLists(ArrayList<String> strList, ArrayList<String> strListNoMaps, SOM_Example ex) {
		resCalcData.clear();
		String outRes;		
		String exOutStr = ""+ex.OID + ","+String.format("%.6f",ex.get_sqDistToBMU())+",";
		SOM_MapNode _exBMU = ex.getBmu();
		if(_exBMU != null) {
			for (ProductExample prod : prodToMapNodes.keySet()) {//for every product
				HashMap<SOM_MapNode, Double> nodeConfsToProds = prodToMapNodes.get(prod);				
				Double conf = nodeConfsToProds.get(_exBMU);//now find confidence of prod in this node's bmu
				if((conf == null) || (conf == 0.0)) {continue;}
				outRes = resCalcData.get(conf);
				if(outRes == null) {outRes = "";}
				outRes +=""+prod.OID+":"+String.format("%.6f",conf)+",";
				resCalcData.put(conf, outRes);
			}
		}
		if (resCalcData.size() > 0) {				
			for(Double conf : resCalcData.keySet()){		exOutStr += resCalcData.get(conf);}
			strList.add(exOutStr);
		} else {				
			exOutStr += "No Mappings With Chosen Products";
			strListNoMaps.add(exOutStr);
		}
	}//buildOutputLists
	

}//StraffProspectOutMapper
