package strafford_SOM_PKG.straff_SOM_Mapping.exampleManagers;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Future;

import base_SOM_Objects.SOM_MapManager;
import base_SOM_Objects.som_examples.ExDataType;
import base_SOM_Objects.som_examples.SOMExample;
import base_SOM_Objects.som_fileIO.SOMExCSVDataLoader;
import base_SOM_Objects.som_utils.runners.SOM_MapExDataToBMUs_Runner;
import base_SOM_Objects.som_utils.runners.SOM_SaveExToBMUs_Runner;
import base_Utils_Objects.io.MsgCodes;
import strafford_SOM_PKG.straff_SOM_Mapping.*;
import strafford_SOM_PKG.straff_SOM_Mapping.exampleManagers.base.Straff_SOMProspectManager;
import strafford_SOM_PKG.straff_Features.featureCalc.Straff_WeightCalc;
import strafford_SOM_PKG.straff_ProcDataHandling.data_loaders.PrscpctCSVDataLoader;
import strafford_SOM_PKG.straff_SOM_Examples.prospects.TrueProspectExample;

public class Straff_SOMTruePrspctManager extends Straff_SOMProspectManager {

	public Straff_SOMTruePrspctManager(SOM_MapManager _mapMgr, String _exName, String _longExampleName) {		super(_mapMgr, _exName, _longExampleName,true);	}//need to validate - useless to map examples that have no corresponding data/are "Bad"
	
	//specific reset functionality for these type of examples
	@Override
	protected void reset_Priv() {}//reset_Priv
	
	///need to validate true prospects - have to have -some- data to be considered useful
	@Override
	protected void validateAndAddExToArray(ArrayList<SOMExample> tmpList, SOMExample ex) {	if(!ex.isBadExample()) {tmpList.add(ex);}}
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
		
		msgObj.dispInfoMessage("Straff_SOMTruePrspctMapper","buildPostFtrVecStructs_Priv","" + TrueProspectExample.NumTPWithNoFtrs+ " True Prospect records with no product-jp-related information - max nonprod jps seen : " +TrueProspectExample.maxNumNonProdJps+" | largest comp vector seen : " + Straff_WeightCalc.numNonProdFtrVecDims+".");
		
		dbg_dispFtrVecMinMaxs(Straff_WeightCalc.mapOfTPCompFtrVecMins, Straff_WeightCalc.mapOfTPCompFtrVecMaxs, "Straff_SOMTruePrspctMapper");

		TrueProspectExample.NumTPWithNoFtrs = 0;
		TrueProspectExample.maxNumNonProdJps =0;
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
	
	
	/**
	 * This exists only for true prospects because TP is the only dataset sufficiently 
	 * large to warrant loading, mapping and saving mappings per perProcData File, 
	 * as opposed to doing each step across all data
	 * @param subdir subdir location of preproc example data
	 * @return
	 */
	public boolean loadPreProcMapBMUAndSaveMappings(String subDir) {
		//first load individual file partition
		String[] loadSrcFNamePrefixAra = projConfigData.buildPreProccedDataCSVFNames_Load(subDir, exampleName+ "MapSrcData");
		int numPartitions = getNumSrcFilePartitions(loadSrcFNamePrefixAra,subDir);
		//load each paritition 1 at a time, calc all features for partition, map to bmus and save mappings
		for(int i=0;i<numPartitions;++i) {
			//clear out all data
			reset();
			
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
			setAllDataLoaded();
			setAllDataPreProcced();
				//data is loaded here, now finalize before ftr calc
			finalizeAllExamples();
				//now build feature vectors
			buildFeatureVectors();	
				//build post-feature vectors - build STD vectors, build alt calc vec mappings
			buildAfterAllFtrVecsBuiltStructs();
				//build array - gets rid of bad examples (have no ftr vector values at all)
			buildExampleArray();
				//launch a MapTestDataToBMUs_Runner to manage multi-threaded calc
			SOM_MapExDataToBMUs_Runner mapRunner = new SOM_MapExDataToBMUs_Runner(mapMgr, th_exec, SOMexampleArray, exampleName, ExDataType.Validation,mapMgr.validateDataMappedIDX, false);	
			mapRunner.runMe();
				//build array again to remove any non-BMU-mapped examples (?)
			//buildExampleArray();
			//(SOM_MapManager _mapMgr, ExecutorService _th_exec, SOMExample[] _exData, String _dataTypName, boolean _forceST, String _fileNamePrefix)
			String _fileNamePrefix = projConfigData.getExampleToBMUFileNamePrefix(exampleName)+"_SrcFileIDX_"+String.format("%02d", i);
			SOM_SaveExToBMUs_Runner saveRunner = new SOM_SaveExToBMUs_Runner(mapMgr, th_exec, SOMexampleArray, exampleName, true,  _fileNamePrefix, preProcDatPartSz);
			saveRunner.runMe();	
		}
		return true;
	}//loadPreProcMapBMUAndSaveMappings
	
	
	
	/**
	 * Save all True Prospect -> BMU mappings
	 */
	@Override
	public boolean saveExampleBMUMappings() {
		if(!isExampleArrayBuilt()) {		buildExampleArray();	}			//incase example array has not yet been built
		
		//(SOM_MapManager _mapMgr, ExecutorService _th_exec, SOMExample[] _exData, String _dataTypName, boolean _forceST, String _fileNamePrefix)
		String _fileNamePrefix = projConfigData.getExampleToBMUFileNamePrefix(exampleName);
		SOM_SaveExToBMUs_Runner saveRunner = new SOM_SaveExToBMUs_Runner(mapMgr, th_exec, SOMexampleArray, exampleName, true,  _fileNamePrefix, preProcDatPartSz);
		saveRunner.runMe();
		
		return true;
	}//saveExampleBMUMappings

}//class Straff_SOMTruePrspctMapper
