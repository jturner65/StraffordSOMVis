package strafford_SOM_PKG.straff_SOM_Mapping.outputMappers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import base_SOM_Objects.som_examples.SOMExample;
import base_SOM_Objects.som_examples.SOMMapNode;
import base_Utils_Objects.io.MessageObject;
import base_Utils_Objects.io.MsgCodes;
import strafford_SOM_PKG.straff_SOM_Examples.products.ProductExample;
import strafford_SOM_PKG.straff_SOM_Mapping.Straff_SOMMapManager;
import strafford_SOM_PKG.straff_SOM_Mapping.outputMappers.base.Straff_ProdMappingOutputBuilder_Base;
import strafford_SOM_PKG.straff_SOM_Mapping.outputMappers.base.Straff_ProdOutMapper_Dist_Base;
import strafford_SOM_PKG.straff_SOM_Mapping.outputMappers.base.Straff_ProspectOutMapper_Dist_Base;
import strafford_SOM_PKG.straff_Utils.Straff_SOMProjConfig;

/**
 * Instances of this object will load a desired set of product IDs and compare them to the constructed SOM to derive appropriate prospects,
 * based on bmu segment membership
 * Listings of these prospects will then be saved to disk
 * @author john
 */
public class Straff_ProdMapOutBldr_Segments extends Straff_ProdMappingOutputBuilder_Base {

	public Straff_ProdMapOutBldr_Segments(Straff_SOMMapManager _mapMgr, String _fileName,ExecutorService _th_exec) {
		super(_mapMgr, _fileName, _th_exec,"Straff_ProdMappingOutputBuilder_Segments");		
	}//ctor

	@Override
	protected String buildFullQualOutPerProspectDir() {
		// TODO Auto-generated method stub - need to determine mechanism for this
		return ((Straff_SOMProjConfig)projConfigData).getFullProdOutMapperBaseDir("_segments");
	}

	@Override
	public void saveAllSpecifiedProdMappings_MT(int numForEachThrd) {
		List<Future<Boolean>> prdcttMapperFtrs = new ArrayList<Future<Boolean>>();
		List<Straff_ProdOutMapper_Dist_Segments> prdcttMappers = new ArrayList<Straff_ProdOutMapper_Dist_Segments>();
		//use this many for every thread but last one
		int stIDX = 0;
		int endIDX = numForEachThrd;				
		for (int i=0; i<(numUsableThreads-1);++i) {				
			prdcttMappers.add(new Straff_ProdOutMapper_Dist_Segments(mapMgr, stIDX, endIDX, i, prodsToMap,fullQualOutPerProdDirs));
			stIDX = endIDX;
			endIDX += numForEachThrd;
		}
		//last one probably won't end at endIDX, so use length
		prdcttMappers.add(new Straff_ProdOutMapper_Dist_Segments(mapMgr,stIDX, prodsToMap.length, numUsableThreads-1, prodsToMap,fullQualOutPerProdDirs));
		try {prdcttMapperFtrs = th_exec.invokeAll(prdcttMappers);for(Future<Boolean> f: prdcttMapperFtrs) { f.get(); }} catch (Exception e) { e.printStackTrace(); }	
	}

	@Override
	public void saveAllSpecifiedProdMappings_ST() {
		//invoke single-threaded version synchronously
		Straff_ProdOutMapper_Dist_Segments mapper = new Straff_ProdOutMapper_Dist_Segments(mapMgr,0, prodsToMap.length, 0, prodsToMap,fullQualOutPerProdDirs);
		mapper.call();
	}

	@Override
	protected HashMap<ProductExample, HashMap<SOMMapNode, Double>> getProdsToMapNodes() {
		// TODO Auto-generated method stub
		//NEED TO BUILD MECHANISM TO Aggregate products
		msgObj.dispMessage("Base->"+callingClassName, "getProdsToMapNodes", "!!!!!!!!!! Building aggregate map of products for prospect mapping in Straff_ProdMappingOutputBuilder_Segments not finished!.", MsgCodes.info5);
		return new HashMap<ProductExample, HashMap<SOMMapNode, Double>>();
	}

	@Override
	protected void saveAllSpecifiedProspectMappings_MT(SOMExample[] prospectsToMap, String typeOfProspect, HashMap<ProductExample, HashMap<SOMMapNode, Double>> prodToMapNodes, int numForEachThrd) {
		List<Future<Boolean>> prdcttMapperFtrs = new ArrayList<Future<Boolean>>();
		List<Straff_ProspectOutMapper_Dist_Segments> prdcttMappers = new ArrayList<Straff_ProspectOutMapper_Dist_Segments>();
		//use this many for every thread but last one
		int stIDX = 0;
		int endIDX = numForEachThrd;				
		for (int i=0; i<(numUsableThreads-1);++i) {		
			prdcttMappers.add(new Straff_ProspectOutMapper_Dist_Segments(msgObj, stIDX, endIDX, i, typeOfProspect, prospectsToMap, prodToMapNodes, fullQualOutPerProspectDir));
			stIDX = endIDX;
			endIDX += numForEachThrd;
		}
		//last one probably won't end at endIDX, so use length
		prdcttMappers.add(new Straff_ProspectOutMapper_Dist_Segments(msgObj,stIDX, prospectsToMap.length, numUsableThreads-1, typeOfProspect, prospectsToMap, prodToMapNodes, fullQualOutPerProspectDir));
		try {prdcttMapperFtrs = th_exec.invokeAll(prdcttMappers);for(Future<Boolean> f: prdcttMapperFtrs) { f.get(); }} catch (Exception e) { e.printStackTrace(); }	
		
	};
	@Override
	protected void saveAllSpecifiedProspectMappings_ST(SOMExample[] prospectsToMap, String typeOfProspect, HashMap<ProductExample, HashMap<SOMMapNode, Double>> prodToMapNodes) {
		//invoke single-threaded version synchronously
		Straff_ProspectOutMapper_Dist_Segments mapper = new Straff_ProspectOutMapper_Dist_Segments(msgObj,0, prospectsToMap.length, 0, typeOfProspect, prospectsToMap, prodToMapNodes, fullQualOutPerProspectDir);
		mapper.call();//			
	}

}//Straff_ProdMappingOutputBuilder_Segments

/////////////////////////
//distance-to-segment-based mappers

class Straff_ProdOutMapper_Dist_Segments extends Straff_ProdOutMapper_Dist_Base{	
	
	public Straff_ProdOutMapper_Dist_Segments(Straff_SOMMapManager _mapMgr, int _stIDX, int _endIDX, int _thdIDX, ProductExample[] _prodsToMap, String[] _fullQualOutDirs) {
		super( _mapMgr, _stIDX,_endIDX, _thdIDX, _prodsToMap, _fullQualOutDirs);
	}//ctor
	
	/**
	* instance-specific code to get each example's list of output
	*/
	protected ArrayList<String> getPerExampleDataAra(ProductExample ex){	return null;}

}//StraffProdOutMapper

class Straff_ProspectOutMapper_Dist_Segments extends Straff_ProspectOutMapper_Dist_Base{

	public Straff_ProspectOutMapper_Dist_Segments(MessageObject _msgObj, int _stIDX, int _endIDX, int _thdIDX,String _prspctType, SOMExample[] _prospectsToMap,	HashMap<ProductExample, HashMap<SOMMapNode, Double>> _prodToMapNodes, String _fullQualOutDir) {
		super(_msgObj, _stIDX, _endIDX, _thdIDX, _prspctType, _prospectsToMap, _prodToMapNodes, _fullQualOutDir, "Straff_ProspectOutMapper_Dist_Segments");
		
	}

	@Override
	protected void setHdrStrings() {
		outHdrStrHasMappings = "Prospect OID,Prospect BMU Dist,Segment etc,...";
		outHdrStrHasNoMappings = "Prospect OID,Prospect BMU Dist,<these prospects have no product mappings among specified products>";	
	}

	@Override
	protected String getFileNameToSave(String _fullQualOutDir, String _prspctType) {return _fullQualOutDir + _prspctType + "_segment_mappingResults_" + thdIDX+".csv";}

	@Override
	protected void buildOutputLists(ArrayList<String> strList, ArrayList<String> strListNoMaps, SOMExample ex) {
		//lists need to be built here
		
	}

}//Straff_ProdOutMapper_Dist_Segments




