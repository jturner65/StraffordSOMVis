package strafford_SOM_PKG.straff_SOM_Mapping.exampleManagers.base;


import java.util.ArrayList;
import java.util.concurrent.ConcurrentSkipListMap;

import base_SOM_Objects.SOM_MapManager;
import base_SOM_Objects.som_examples.SOMExample;
import base_Utils_Objects.io.MsgCodes;
import strafford_SOM_PKG.straff_SOM_Mapping.*;

/**
 * this mapper exists mainly to manage IO for customer and true prospects pre-procced data. 
 * @author john
 */

public abstract class Straff_SOMProspectManager extends Straff_SOMExampleManager {
	protected final int preProcDatPartSz;
	public Straff_SOMProspectManager(SOM_MapManager _mapMgr, String _exName, String _longExampleName, boolean _shouldValidate) {
		super(_mapMgr,  _exName, _longExampleName, _shouldValidate);		
		preProcDatPartSz = Straff_SOMMapManager.preProcDatPartSz;
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
			SOMExample ex1 = exampleMap.get(exampleMap.firstKey());
			String hdrStr = ex1.getRawDescColNamesForCSV();
			csvResTmp.add( hdrStr);
			int nameCounter = 0, numFiles = (1+((int)((exampleMap.size()-1)/preProcDatPartSz)));
			msgObj.dispMessage("Straff_SOMExampleMapper","saveAllExampleMapData","Start Building "+exampleName+" String Array : " +nameCounter + " of "+numFiles+".", MsgCodes.info1);
			for (SOMExample ex : exampleMap.values()) {			
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