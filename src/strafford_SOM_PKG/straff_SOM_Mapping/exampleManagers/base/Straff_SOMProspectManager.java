package strafford_SOM_PKG.straff_SOM_Mapping.exampleManagers.base;


import java.util.ArrayList;
import java.util.concurrent.ConcurrentSkipListMap;

import base_SOM_Objects.SOM_MapManager;
import base_SOM_Objects.som_examples.SOM_ExDataType;
import base_SOM_Objects.som_examples.SOM_Example;
import base_SOM_Objects.som_utils.runners.SOM_MapExDataToBMUs_Runner;
import base_SOM_Objects.som_utils.runners.SOM_SaveExToBMUs_Runner;
import base_Utils_Objects.io.MsgCodes;
import strafford_SOM_PKG.straff_SOM_Examples.prospects.Straff_TrueProspectExample;
import strafford_SOM_PKG.straff_SOM_Mapping.*;

/**
 * this mapper exists mainly to manage IO for customer and true prospects pre-procced data. 
 * @author john
 */

public abstract class Straff_SOMProspectManager extends Straff_SOMExampleManager {
	
	public Straff_SOMProspectManager(SOM_MapManager _mapMgr, String _exName, String _longExampleName, boolean _shouldValidate) {
		super(_mapMgr,  _exName, _longExampleName, _shouldValidate);	
		
	}
	
	/**
	 * This exists for very large data sets, to warrant and enable 
	 * loading, mapping and saving bmu mappings per perProcData File, 
	 * as opposed to doing each step across all data
	 * @param subdir subdir location of preproc example data
	 * @param dataType type of data being processed
	 * @param dataMappedIDX index in boolean state flags in map manager denoting whether this data type has been mapped or not
	 * @return
	 */
	public final boolean loadDataMapBMUAndSavePerPreProcFile(String subDir, SOM_ExDataType dataType, int dataMappedIDX) {
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
				Straff_TrueProspectExample ex = new Straff_TrueProspectExample(mapMgr, oid, str);
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
			SOM_MapExDataToBMUs_Runner mapRunner = new SOM_MapExDataToBMUs_Runner(mapMgr, th_exec, SOMexampleArray, exampleName, dataType,dataMappedIDX, false);	
			mapRunner.runMe();
				//build array again to remove any non-BMU-mapped examples (?)
			//buildExampleArray();
			//(SOM_MapManager _mapMgr, ExecutorService _th_exec, SOMExample[] _exData, String _dataTypName, boolean _forceST, String _fileNamePrefix)
			String _fileNamePrefix = projConfigData.getExampleToBMUFileNamePrefix(exampleName)+"_SrcFileIDX_"+String.format("%02d", i);
			SOM_SaveExToBMUs_Runner saveRunner = new SOM_SaveExToBMUs_Runner(mapMgr, th_exec, SOMexampleArray, exampleName, true,  _fileNamePrefix, preProcDatPartSz);
			saveRunner.runMe();	
		}
		return true;
	}//loadDataMapBMUAndSavePerPreProcFile

	
	protected final void dbg_dispFtrVecMinMaxs(ConcurrentSkipListMap<Integer, Float> minMap, ConcurrentSkipListMap<Integer, Float> maxMap, String callClass) {	
		msgObj.dispInfoMessage(callClass,"dbg_dispFtrVecMinMaxs","Start JP,Min Value,Max Value.");
		for(Integer idx : minMap.keySet()) {
			Integer jp = jpJpgrpMon.getFtrJpByIdx(idx);
			msgObj.dispInfoMessage(callClass,"dbg_dispFtrVecMinMaxs",""+jp+","+minMap.get(idx)+","+maxMap.get(idx));
		}
		minMap.clear();
		maxMap.clear();
		msgObj.dispInfoMessage(callClass,"dbg_dispFtrVecMinMaxs","Finished JP,Min Value,Max Value.");
	}


}//class Straff_SOMProspectMapper