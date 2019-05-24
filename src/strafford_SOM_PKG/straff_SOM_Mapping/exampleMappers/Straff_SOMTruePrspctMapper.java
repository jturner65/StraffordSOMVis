package strafford_SOM_PKG.straff_SOM_Mapping.exampleMappers;

import java.util.*;
import java.util.concurrent.Future;

import base_SOM_Objects.SOMMapManager;
import base_SOM_Objects.som_examples.SOMExample;
import base_SOM_Objects.som_fileIO.SOMExCSVDataLoader;

import strafford_SOM_PKG.straff_SOM_Mapping.*;
import strafford_SOM_PKG.straff_ProcDataHandling.data_loaders.PrscpctCSVDataLoader;
import strafford_SOM_PKG.straff_SOM_Examples.prospects.CustProspectExample;
import strafford_SOM_PKG.straff_SOM_Examples.prospects.TrueProspectExample;
import strafford_SOM_PKG.straff_Utils.featureCalc.StraffWeightCalc;

public class Straff_SOMTruePrspctMapper extends Straff_SOMProspectMapper {


	public Straff_SOMTruePrspctMapper(SOMMapManager _mapMgr, String _exName) {		super(_mapMgr, _exName);	}
	
	//specific reset functionality for these type of examples
	@Override
	protected void reset_Priv() {}//reset_Priv
	
	///no validation performed for true prospects - all are welcome
	@Override
	protected void validateAndAddEx(ArrayList<SOMExample> tmpList, SOMExample ex) {	tmpList.add(ex);		}
	@Override
	//add example from map to array without validation
	protected SOMExample[] noValidateBuildExampleArray() {	return exampleMap.values().toArray(new SOMExample[0]);};	
	@Override
	//after example array has been built, and specific funcitonality for these types of examples
	protected void buildExampleArrayEnd_Priv(boolean validate) {}
	
	@Override
	//build either true prospect feature vectors
	protected void buildFtrVec_Priv() {
		jpJpgrpMon = ((Straff_SOMMapManager)mapMgr).jpJpgrpMon;
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

	
	@Override
	//manage multi-threaded loading
	protected void buildMTLoader(String[] loadSrcFNamePrefixAra, int numPartitions) {
		List<Future<Boolean>> preProcLoadFtrs = new ArrayList<Future<Boolean>>();
		List<SOMExCSVDataLoader> preProcLoaders = new ArrayList<SOMExCSVDataLoader>();
		for (int i=0; i<numPartitions;++i) {	preProcLoaders.add(new PrscpctCSVDataLoader(mapMgr, i, loadSrcFNamePrefixAra[0]+"_"+i+".csv",  exampleName+ " Data file " + i +" of " +numPartitions +" loaded",  exampleName+ " Data File " + i +" of " +numPartitions +" Failed to load", exampleMap));}
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

	@Override
	protected SOMExample[] castArray(ArrayList<SOMExample> tmpList) {	return (TrueProspectExample[])(tmpList.toArray(new TrueProspectExample[0]));}

}//class Straff_SOMTruePrspctMapper
