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
		
	//load prospect mapped training data into StraffSOMExamples from disk
	//must reset prospect/validation maps before this is called
	@Override
	public final void loadAllPreProccedMapData(String subDir) {
		//perform in multiple threads if possible
		msgObj.dispMessage("Straff_SOMProspectMapper::"+exampleName,"loadAllPreProccedMapData","Loading all " + exampleName+ " map data that have event-based training info from : " +subDir, MsgCodes.info5);//" + (eventsOnly ? "that only have event-based training info" : "that have any training info (including only prospect jpg/jp specification)"));
		//all data managed by this example mapper needs to be reset
		reset();
		String[] loadSrcFNamePrefixAra = projConfigData.buildPreProccedDataCSVFNames_Load(subDir, exampleName+ "MapSrcData");
		//get number of paritions
		int numPartitions = getNumSrcFilePartitions(loadSrcFNamePrefixAra,subDir);
		//error loading
		if(numPartitions == -1) {return;}
		//load data creation date time, if exists
		loadDataCreateDateTime(subDir);
		
		boolean canMultiThread=mapMgr.isMTCapable();
		if(canMultiThread) {			buildMTLoader(loadSrcFNamePrefixAra, numPartitions);	} 
		else {							buildSTLoader(loadSrcFNamePrefixAra, numPartitions);	}
		setAllDataLoaded();
		setAllDataPreProcced();
		msgObj.dispMessage("Straff_SOMProspectMapper::"+exampleName,"loadAllPreProccedMapData","Finished loading and preprocessing all local prospect map data.  Number of entries in " + exampleName + " prospectMap : " + exampleMap.size(), MsgCodes.info5);
	}//loadAllPropsectMapData	
	
	/**
	 * load prospect data format file holding # of csv files of this kind of prospect data and return value
	 * @param subDir
	 * @return number of source file partitions of this type of preprocessed data
	 */
	protected final int getNumSrcFilePartitions(String[] loadSrcFNamePrefixAra, String subDir) {
		String fmtFile = loadSrcFNamePrefixAra[0]+"_format.csv";
		String[] loadRes = fileIO.loadFileIntoStringAra(fmtFile, exampleName+" Format file loaded", exampleName+" Format File Failed to load");
		
		int numPartitions = 0;
		try {
			numPartitions = Integer.parseInt(loadRes[0].split(" : ")[1].trim());
		} catch (Exception e) {e.printStackTrace(); msgObj.dispMessage("Straff_SOMProspectMapper::"+exampleName,"loadAllPreProccedMapData","Due to error with not finding format file : " + fmtFile+ " no data will be loaded.", MsgCodes.error1); return -1;} 
		return numPartitions;
	}//getNumSrcFilePartitions
	
	
	protected abstract void buildMTLoader(String[] loadSrcFNamePrefixAra, int numPartitions);
	protected abstract void buildSTLoader(String[] loadSrcFNamePrefixAra, int numPartitions);	
	
	//save all pre-processed prospect data
	@Override
	public final boolean saveAllPreProccedMapData() {
		if ((null != exampleMap) && (exampleMap.size() > 0)) {
			msgObj.dispMessage("Straff_SOMExampleMapper","saveAllExampleMapData","Saving all "+exampleName+" map data : " + exampleMap.size() + " examples to save.", MsgCodes.info5);
			//save date/time of data creation
			saveDataCreateDateTime();
			
			String[] saveDestFNamePrefixAra = projConfigData.buildPreProccedDataCSVFNames_Save(exampleName+"MapSrcData");
			ArrayList<String> csvResTmp = new ArrayList<String>();		
			int counter = 0;
			SOM_Example ex1 = exampleMap.get(exampleMap.firstKey());
			String hdrStr = ex1.getRawDescColNamesForCSV();
			csvResTmp.add( hdrStr);
			int nameCounter = 0, numFiles = (1+((int)((exampleMap.size()-1)/preProcDatPartSz)));
			msgObj.dispMessage("Straff_SOMExampleMapper","saveAllExampleMapData","Start Building "+exampleName+" String Array : " +nameCounter + " of "+numFiles+".", MsgCodes.info1);
			for (SOM_Example ex : exampleMap.values()) {			
				csvResTmp.add(ex.getPreProcDescrForCSV());
				++counter;
				if(counter % preProcDatPartSz ==0) {
					String fileName = saveDestFNamePrefixAra[0]+"_"+nameCounter+".csv";
					msgObj.dispMessage("Straff_SOMExampleMapper","saveAllExampleMapData","Done Building "+exampleName+" String Array : " +nameCounter + " of "+numFiles+".  Saving to file : "+fileName, MsgCodes.info1);
					//csvRes.add(csvResTmp); 
					fileIO.saveStrings(fileName, csvResTmp);
					csvResTmp = new ArrayList<String>();
					csvResTmp.add( hdrStr);
					counter = 0;
					++nameCounter;
					msgObj.dispMessage("Straff_SOMExampleMapper","saveAllExampleMapData","Start Building "+exampleName+" String Array : " +nameCounter + " of "+numFiles+".", MsgCodes.info1);
				}
			}
			//last array if has values
			if(csvResTmp.size() > 1) {	
				String fileName = saveDestFNamePrefixAra[0]+"_"+nameCounter+".csv";
				msgObj.dispMessage("Straff_SOMExampleMapper","saveAllExampleMapData","Done Building "+exampleName+" String Array : " +nameCounter + " of "+numFiles+".  Saving to file : "+fileName, MsgCodes.info1);
				//csvRes.add(csvResTmp);
				fileIO.saveStrings(fileName, csvResTmp);
				csvResTmp = new ArrayList<String>();
				++nameCounter;
			}			
			msgObj.dispMessage("Straff_SOMExampleMapper","saveAllExampleMapData","Finished partitioning " + exampleMap.size()+ " "+exampleName+" records into " + nameCounter + " "+exampleName+" record files, each holding up to " + preProcDatPartSz + " records and saving to files.", MsgCodes.info1);
			//save the data in a format file
			String[] data = new String[] {"Number of file partitions for " + saveDestFNamePrefixAra[1] +" data : "+ nameCounter + "\n"};
			fileIO.saveStrings(saveDestFNamePrefixAra[0]+"_format.csv", data);		
			msgObj.dispMessage("Straff_SOMExampleMapper","saveAllExampleMapData","Finished saving all "+exampleName+" map data", MsgCodes.info5);
			return true;
		} else {msgObj.dispMessage("Straff_SOMExampleMapper","saveAllExampleMapData","No "+exampleName+" example data to save. Aborting", MsgCodes.error2); return false;}
	}//saveAllPreProccedMapData
	
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