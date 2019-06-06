package strafford_SOM_PKG.straff_SOM_Mapping.exampleMappers;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Future;

import base_SOM_Objects.SOMMapManager;
import base_SOM_Objects.som_examples.SOMExample;
import base_SOM_Objects.som_fileIO.SOMExCSVDataLoader;
import base_Utils_Objects.MsgCodes;
import strafford_SOM_PKG.straff_SOM_Mapping.*;
import strafford_SOM_PKG.straff_Features.featureCalc.StraffWeightCalc;
import strafford_SOM_PKG.straff_ProcDataHandling.data_loaders.PrscpctCSVDataLoader;
import strafford_SOM_PKG.straff_SOM_Examples.prospects.TrueProspectExample;

public class Straff_SOMTruePrspctMapper extends Straff_SOMProspectMapper {

	public Straff_SOMTruePrspctMapper(SOMMapManager _mapMgr, String _exName, String _longExampleName, boolean _shouldValidate) {		super(_mapMgr, _exName, _longExampleName,_shouldValidate);	}
	
	//specific reset functionality for these type of examples
	@Override
	protected void reset_Priv() {}//reset_Priv
	
	///no validation performed for true prospects - all are welcome
	@Override
	protected void validateAndAddExToArray(ArrayList<SOMExample> tmpList, SOMExample ex) {	tmpList.add(ex);		}
	@Override
	//add example from map to array without validation
	protected SOMExample[] noValidateBuildExampleArray() {	return (TrueProspectExample[])(exampleMap.values().toArray(new TrueProspectExample[0]));};	
	@Override
	protected SOMExample[] castArray(ArrayList<SOMExample> tmpList) {	return (TrueProspectExample[])(tmpList.toArray(new TrueProspectExample[0]));}
	@Override
	//after example array has been built, and specific funcitonality for these types of examples
	protected void buildExampleArrayEnd_Priv(boolean validate) {}
	
	@Override
	//build either true prospect feature vectors
	protected void buildStraffFtrVec_Priv() {
		//reset calc analysis objects before building feature vectors to enable new analytic info to be aggregateds
		((Straff_SOMMapManager)mapMgr).ftrCalcObj.resetCalcObjs(StraffWeightCalc.tpCalcObjIDX);
		//call to buildFeatureVector for all examples
		mapMgr._ftrVecBuild(exampleMap.values(),0,exampleName);				
		//call to _ftrVecBuild() with _typeOfProc==1 calls postFtrVecBuild for all examples of specified type - strafford data doesn't currently use this functionality so we can comment this call
		//mapMgr._ftrVecBuild(exs, 1, exType);	
		//set calc/calc analysis state as finished
		((Straff_SOMMapManager)mapMgr).ftrCalcObj.finishFtrCalcs(StraffWeightCalc.tpCalcObjIDX);	
	}//buildFtrVecs NumBadExamplesAfterFtrsBuilt
	//instance-specific code to execute after examples have had ftrs calculated
	
	//debugging functionality 
	private void dispCompFtrVecRes() {
		msgObj.dispInfoMessage("Straff_SOMTruePrspctMapper","buildPostFtrVecStructs_Priv","Showing Results of mapping avg ftr vec to appropriate training examples by matching source data");
		msgObj.dispInfoMessage("Straff_SOMTruePrspctMapper","buildPostFtrVecStructs_Priv","Size of Comp Vec,# of True Prospects with this size Comp Vec,# of actual ftr configs for this count.");

		for(Integer sizeOfCompVec : StraffWeightCalc.mapOfNonProdFtrVecDims.keySet()) {
			ConcurrentSkipListMap<Integer, Integer> mapOfFtrs = StraffWeightCalc.mapOfTPNonProdJpsPerSizeNonProdFtrVec.get(sizeOfCompVec);
			String tmp = "";
			for(Integer jp : mapOfFtrs.keySet()) {tmp += ""+jp+","+mapOfFtrs.get(jp)+",";}
			msgObj.dispInfoMessage("Straff_SOMTruePrspctMapper","buildPostFtrVecStructs_Priv","\t" + sizeOfCompVec + ", " + StraffWeightCalc.mapOfNonProdFtrVecDims.get(sizeOfCompVec)+ ", " +mapOfFtrs.size() + "," + tmp);
		}
		
		msgObj.dispInfoMessage("Straff_SOMTruePrspctMapper","buildPostFtrVecStructs_Priv","" + TrueProspectExample.NumTPWithNoFtrs+ " True Prospect records with no product-jp-related information - max nonprod jps seen : " +TrueProspectExample.maxNumNonProdJps+" | largest comp vector seen : " + StraffWeightCalc.numNonProdFtrVecDims+".");
		
		dbg_dispFtrVecMinMaxs(StraffWeightCalc.mapOfTPCompFtrVecMins, StraffWeightCalc.mapOfTPCompFtrVecMaxs, "Straff_SOMTruePrspctMapper");

		TrueProspectExample.NumTPWithNoFtrs = 0;
		TrueProspectExample.maxNumNonProdJps =0;
		StraffWeightCalc.numNonProdFtrVecDims= 0;
		StraffWeightCalc.mapOfNonProdFtrVecDims.clear();
		StraffWeightCalc.mapOfTPNonProdJpsPerSizeNonProdFtrVec.clear();
	}//dispCompFtrVecRes
	
	/**
	 * code to execute after examples have had ftrs calculated - this will calculate std features and any alternate ftr mappings if used
	 */
	@Override
	protected void buildAfterAllFtrVecsBuiltStructs_Priv() {
		//call to buildFeatureVector for all examples
		mapMgr._ftrVecBuild(exampleMap.values(),2,exampleName);	
		//display results of building comparison vector for all true prospects
		dispCompFtrVecRes();
	}
	
	@Override
	//manage multi-threaded loading
	protected void buildMTLoader(String[] loadSrcFNamePrefixAra, int numPartitions) {
		List<Future<Boolean>> preProcLoadFtrs = new ArrayList<Future<Boolean>>();
		List<SOMExCSVDataLoader> preProcLoaders = new ArrayList<SOMExCSVDataLoader>();
		msgObj.dispMessage("Straff_SOMTruePrspctMapper::"+exampleName,"buildMTLoader","Building " +numPartitions+" " + exampleName+ " data multi-threaded loaders.", MsgCodes.info5);
		for (int i=0; i<numPartitions;++i) {	preProcLoaders.add(new PrscpctCSVDataLoader(mapMgr, i, loadSrcFNamePrefixAra[0]+"_"+i+".csv",  exampleName+ " Data file " + i +" of " +numPartitions +" loaded",  exampleName+ " Data File " + i +" of " +numPartitions +" Failed to load", exampleMap));}
		msgObj.dispMessage("Straff_SOMTruePrspctMapper::"+exampleName,"buildMTLoader","Launching " +numPartitions+" " + exampleName+ " data multi-threaded loaders.", MsgCodes.info5);
		try {preProcLoadFtrs = th_exec.invokeAll(preProcLoaders);for(Future<Boolean> f: preProcLoadFtrs) { 			f.get(); 		}} catch (Exception e) { e.printStackTrace(); }					
	}//buildMTLoader
	
	@Override
	//manage single threaded loading
	protected void buildSTLoader(String[] loadSrcFNamePrefixAra, int numPartitions) {
		for (int i=numPartitions-1; i>=0;--i) {
			String dataFile = loadSrcFNamePrefixAra[0]+"_"+i+".csv";
			String[] csvLoadRes = fileIO.loadFileIntoStringAra(dataFile, exampleName+ " Data file " + i +" of " +numPartitions +" loaded",  exampleName+ " Data File " + i +" of " +numPartitions +" Failed to load");
			//ignore first entry - header
			for (int j=1;j<csvLoadRes.length; ++j) {
				String str = csvLoadRes[j];
				int pos = str.indexOf(',');
				String oid = str.substring(0, pos);
				TrueProspectExample ex = new TrueProspectExample(mapMgr, oid, str);
				exampleMap.put(oid, ex);			
			}
		}				
	}//buildSTLoader

}//class Straff_SOMTruePrspctMapper
