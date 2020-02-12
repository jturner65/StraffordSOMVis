package strafford_SOM_PKG.straff_SOM_Mapping.exampleManagers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Future;

import base_SOM_Objects.SOM_MapManager;
import base_SOM_Objects.som_examples.SOM_Example;
import base_SOM_Objects.som_fileIO.SOM_ExCSVDataLoader;
import base_Utils_Objects.io.MsgCodes;
import strafford_SOM_PKG.straff_Features.featureCalc.Straff_WeightCalc;
import strafford_SOM_PKG.straff_SOM_Examples.prospects.Straff_TrueProspectExample;
import strafford_SOM_PKG.straff_SOM_Mapping.Straff_SOMMapManager;
import strafford_SOM_PKG.straff_SOM_Mapping.exampleManagers.base.Straff_SOMExampleManager;
import strafford_SOM_PKG.straff_SOM_Mapping.procData_loaders.Straff_PrscpctCSVDataLoader;

public class Straff_SOMTruePrspctManager extends Straff_SOMExampleManager {

	public Straff_SOMTruePrspctManager(SOM_MapManager _mapMgr, String _exName, String _longExampleName) {		super(_mapMgr, _exName, _longExampleName,new boolean[] {true, false});	}//need to validate - useless to map examples that have no corresponding data/are "Bad"
	
	//specific reset functionality for these type of examples
	@Override
	protected void reset_Priv() {}//reset_Priv
	
	///need to validate true prospects - have to have -some- data to be considered useful
	@Override
	protected void validateAndAddExToArray(ArrayList<SOM_Example> tmpList, SOM_Example ex) {	if(!ex.isBadExample()) {tmpList.add(ex);}}
	@Override
	//add example from map to array without validation
	protected SOM_Example[] noValidateBuildExampleArray() {	return (Straff_TrueProspectExample[])(exampleMap.values().toArray(new Straff_TrueProspectExample[0]));};	
	@Override
	protected SOM_Example[] castArray(ArrayList<SOM_Example> tmpList) {	return (Straff_TrueProspectExample[])(tmpList.toArray(new Straff_TrueProspectExample[0]));}
	@Override
	//after example array has been built, and specific funcitonality for these types of examples
	protected void buildExampleArrayEnd_Priv(boolean validate) {}
	
	@Override
	//build either true prospect feature vectors
	protected void buildStraffFtrVec_Priv() {
		//reset calc analysis objects before building feature vectors to enable new analytic info to be aggregateds
		((Straff_SOMMapManager)mapMgr).ftrCalcObj.resetCalcObjs(Straff_WeightCalc.tpCalcObjIDX);
		//call to buildFeatureVector for all examples
		mapMgr._ftrVecBuild(exampleMap.values(),0,exampleName, true);				
		//call to _ftrVecBuild() with _typeOfProc==1 calls postFtrVecBuild for all examples of specified type - strafford data doesn't currently use this functionality so we can comment this call
		//mapMgr._ftrVecBuild(exs, 1, exType);	
		//set calc/calc analysis state as finished
		((Straff_SOMMapManager)mapMgr).ftrCalcObj.finishFtrCalcs(Straff_WeightCalc.tpCalcObjIDX);	
	}//buildFtrVecs NumBadExamplesAfterFtrsBuilt
	//instance-specific code to execute after examples have had ftrs calculated
	
	//debugging functionality 
	private void dispCompFtrVecRes() {
		msgObj.dispInfoMessage("Straff_SOMTruePrspctMapper","buildPostFtrVecStructs_Priv","Showing Results of mapping avg ftr vec to appropriate training examples by matching source data");
		msgObj.dispInfoMessage("Straff_SOMTruePrspctMapper","buildPostFtrVecStructs_Priv","Size of Comp Vec,# of True Prospects with this size Comp Vec,# of actual ftr configs for this count | nonprod jp, count of nonprod jp, etc...");

		for(Integer sizeOfCompVec : Straff_WeightCalc.mapOfNonProdFtrVecDims.keySet()) {
			ConcurrentSkipListMap<Integer, Integer> mapOfFtrs = Straff_WeightCalc.mapOfTPNonProdJpsPerSizeNonProdFtrVec.get(sizeOfCompVec);
			String tmp = "";
			for(Integer jp : mapOfFtrs.keySet()) {tmp += ""+jp+","+mapOfFtrs.get(jp)+",";}
			msgObj.dispInfoMessage("Straff_SOMTruePrspctMapper","buildPostFtrVecStructs_Priv","\t" + sizeOfCompVec + ",\t" + Straff_WeightCalc.mapOfNonProdFtrVecDims.get(sizeOfCompVec)+ ",\t" +mapOfFtrs.size() + " |\t" + tmp);
		}
		
		msgObj.dispInfoMessage("Straff_SOMTruePrspctMapper","buildPostFtrVecStructs_Priv","" + Straff_TrueProspectExample.NumTPWithNoFtrs+ " True Prospect records with no product-jp-related information - max nonprod jps seen : " +Straff_TrueProspectExample.maxNumNonProdJps+" | largest comp vector seen : " + Straff_WeightCalc.numNonProdFtrVecDims+".");
		
		dbg_dispFtrVecMinMaxs(Straff_WeightCalc.mapOfTPCompFtrVecMins, Straff_WeightCalc.mapOfTPCompFtrVecMaxs, "Straff_SOMTruePrspctMapper");

		Straff_TrueProspectExample.NumTPWithNoFtrs = 0;
		Straff_TrueProspectExample.maxNumNonProdJps =0;
		Straff_WeightCalc.numNonProdFtrVecDims= 0;
		Straff_WeightCalc.mapOfNonProdFtrVecDims.clear();
		Straff_WeightCalc.mapOfTPNonProdJpsPerSizeNonProdFtrVec.clear();
	}//dispCompFtrVecRes
	
	/**
	 * code to execute after examples have had ftrs calculated - this will calculate std features and any alternate ftr mappings if used
	 */
	@Override
	protected void buildAfterAllFtrVecsBuiltStructs_Priv() {
		//call to buildFeatureVector for all examples
		mapMgr._ftrVecBuild(exampleMap.values(),2,exampleName, true);	
		//display results of building comparison vector for all true prospects
		dispCompFtrVecRes();
	}
	
	@Override
	//manage multi-threaded loading
	protected void buildMTLoader(String[] loadSrcFNamePrefixAra, int numPartitions) {
		List<Future<Boolean>> preProcLoadFtrs = new ArrayList<Future<Boolean>>();
		List<SOM_ExCSVDataLoader> preProcLoaders = new ArrayList<SOM_ExCSVDataLoader>();
		msgObj.dispMessage("Straff_SOMTruePrspctMapper::"+exampleName,"buildMTLoader","Building " +numPartitions+" " + exampleName+ " data multi-threaded loaders.", MsgCodes.info5);
		for (int i=0; i<numPartitions;++i) {	preProcLoaders.add(new Straff_PrscpctCSVDataLoader(mapMgr, i, loadSrcFNamePrefixAra[0]+"_"+i+".csv",  exampleName+ " Data file " + i +" of " +numPartitions +" loaded",  exampleName+ " Data File " + i +" of " +numPartitions +" Failed to load", exampleMap));}
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
				Straff_TrueProspectExample ex = new Straff_TrueProspectExample(mapMgr, oid, str);
				exampleMap.put(oid, ex);			
			}
		}				
	}//buildSTLoader
		
	
//	/**
//	 * Save all True Prospect -> BMU mappings
//	 */
//	@Override
//	public boolean saveExampleBMUMappings() {
//		if(!isExampleArrayBuilt()) {		buildExampleArray();	}			//incase example array has not yet been built
//		
//		//(SOM_MapManager _mapMgr, ExecutorService _th_exec, SOMExample[] _exData, String _dataTypName, boolean _forceST, String _fileNamePrefix)
//		String _fileNamePrefix = projConfigData.getExampleToBMUFileNamePrefix(exampleName);
//		SOM_SaveExToBMUs_Runner saveRunner = new SOM_SaveExToBMUs_Runner(mapMgr, th_exec, SOMexampleArray, exampleName, true,  _fileNamePrefix, preProcDatPartSz);
//		saveRunner.runMe();
//		
//		return true;
//	}//saveExampleBMUMappings
	
	/**
	 * return array of examples to save their bmus - called from saveExampleBMUMappings in Straff_SOMExampleManager
	 * @return
	 */
	@Override
	protected final SOM_Example[] getExToSave() {
		if(!isExampleArrayBuilt()) {		buildExampleArray();	}	//incase example array has not yet been built
		msgObj.dispInfoMessage("Straff_SOMTruePrspctManager","getExToSave","Size of exToSaveBMUs : " + SOMexampleArray.length);
		return SOMexampleArray;
	}

	@Override
	protected SOM_Example buildSingleExample(String _oid, String _str) {		return new Straff_TrueProspectExample(mapMgr, _oid, _str);	};

}//class Straff_SOMTruePrspctMapper
