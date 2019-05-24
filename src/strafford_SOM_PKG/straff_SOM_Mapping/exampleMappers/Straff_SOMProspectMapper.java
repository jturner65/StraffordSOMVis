package strafford_SOM_PKG.straff_SOM_Mapping.exampleMappers;


import java.util.ArrayList;

import base_SOM_Objects.SOMMapManager;
import base_SOM_Objects.som_examples.SOMExample;
import base_Utils_Objects.MsgCodes;
import strafford_SOM_PKG.straff_SOM_Mapping.*;

/**
 * this mapper exists mainly to manage IO for customer and true prospects pre-procced data
 * @author john
 */

public abstract class Straff_SOMProspectMapper extends Straff_SOMExampleMapper {
	private final int preProcDatPartSz;
	public Straff_SOMProspectMapper(SOMMapManager _mapMgr, String _exName) {
		super(_mapMgr,  _exName);		
		preProcDatPartSz = ((Straff_SOMMapManager)mapMgr).preProcDatPartSz;
	}
	
	
	//load prospect mapped training data into StraffSOMExamples from disk
	//must reset prospect/validation maps before this is called
	@Override
	public void loadAllPreProccedMapData(String subDir) {
		//perform in multiple threads if possible
		msgObj.dispMessage("Straff_SOMProspectMapper::"+exampleName,"loadAllPreProccedMapData","Loading all " + exampleName+ " map data that only have event-based training info from : " +subDir, MsgCodes.info5);//" + (eventsOnly ? "that only have event-based training info" : "that have any training info (including only prospect jpg/jp specification)"));
		//all data managed by this example mapper needs to be reset
		reset();
		//get 
		String[] loadSrcFNamePrefixAra = projConfigData.buildProccedDataCSVFNames(subDir, true, exampleName+ "MapSrcData");
		String fmtFile = loadSrcFNamePrefixAra[0]+"_format.csv";
		
		String[] loadRes = fileIO.loadFileIntoStringAra(fmtFile, "Format file loaded", "Format File Failed to load");
		int numPartitions = 0;
		try {
			numPartitions = Integer.parseInt(loadRes[0].split(" : ")[1].trim());
		} catch (Exception e) {e.printStackTrace(); msgObj.dispMessage("Straff_SOMProspectMapper::"+exampleName,"loadAllPreProccedMapData","Due to error with not finding format file : " + fmtFile+ " no data will be loaded.", MsgCodes.error1); return;} 
		
		boolean canMultiThread=mapMgr.isMTCapable();//this means the current machine only has 1 or 2 available processors, numUsableThreads == # available - 2
		if(canMultiThread) {			buildMTLoader(loadSrcFNamePrefixAra, numPartitions);	} 
		else {							buildSTLoader(loadSrcFNamePrefixAra, numPartitions);	}
		setAllDataLoaded();
		setAllDataPreProcced();
		msgObj.dispMessage("Straff_SOMProspectMapper::"+exampleName,"loadAllPreProccedMapData","Finished loading and preprocessing all local prospect map data and calculating features.  Number of entries in " + exampleName + " prospectMap : " + exampleMap.size(), MsgCodes.info5);
	}//loadAllPropsectMapData	
	
	protected abstract void buildMTLoader(String[] loadSrcFNamePrefixAra, int numPartitions);
	protected abstract void buildSTLoader(String[] loadSrcFNamePrefixAra, int numPartitions);
	
	//save all pre-processed prospect data
	@Override
	public boolean saveAllPreProccedMapData() {
		if ((null != exampleMap) && (exampleMap.size() > 0)) {
			msgObj.dispMessage("StraffSOMMapManager","saveAllExampleMapData","Saving all "+exampleMap+" map data : " + exampleMap.size() + " examples to save.", MsgCodes.info5);
			String[] saveDestFNamePrefixAra = projConfigData.buildProccedDataCSVFNames(true, exampleMap+"MapSrcData");
			ArrayList<String> csvResTmp = new ArrayList<String>();		
			int counter = 0;
			SOMExample ex1 = exampleMap.get(exampleMap.firstKey());
			String hdrStr = ex1.getRawDescColNamesForCSV();
			csvResTmp.add( hdrStr);
			int nameCounter = 0, numFiles = (1+((int)((exampleMap.size()-1)/preProcDatPartSz)));
			msgObj.dispMessage("StraffSOMMapManager","saveAllExampleMapData","Start Building "+exampleMap+" String Array : " +nameCounter + " of "+numFiles+".", MsgCodes.info1);
			for (SOMExample ex : exampleMap.values()) {			
				csvResTmp.add(ex.getRawDescrForCSV());
				++counter;
				if(counter % preProcDatPartSz ==0) {
					String fileName = saveDestFNamePrefixAra[0]+"_"+nameCounter+".csv";
					msgObj.dispMessage("StraffSOMMapManager","saveAllExampleMapData","Done Building "+exampleMap+" String Array : " +nameCounter + " of "+numFiles+".  Saving to file : "+fileName, MsgCodes.info1);
					//csvRes.add(csvResTmp); 
					fileIO.saveStrings(fileName, csvResTmp);
					csvResTmp = new ArrayList<String>();
					csvResTmp.add( hdrStr);
					counter = 0;
					++nameCounter;
					msgObj.dispMessage("StraffSOMMapManager","saveAllExampleMapData","Start Building "+exampleMap+" String Array : " +nameCounter + " of "+numFiles+".", MsgCodes.info1);
				}
			}
			if(csvResTmp.size() > 1) {	
				String fileName = saveDestFNamePrefixAra[0]+"_"+nameCounter+".csv";
				msgObj.dispMessage("StraffSOMMapManager","saveAllExampleMapData","Done Building "+exampleMap+" String Array : " +nameCounter + " of "+numFiles+".  Saving to file : "+fileName, MsgCodes.info1);
				//csvRes.add(csvResTmp);
				fileIO.saveStrings(fileName, csvResTmp);
				csvResTmp = new ArrayList<String>();
				++nameCounter;
			}			
			msgObj.dispMessage("StraffSOMMapManager","saveAllExampleMapData","Finished partitioning " + exampleMap.size()+ " "+exampleMap+" records into " + nameCounter + " "+exampleMap+" record files, each holding up to " + preProcDatPartSz + " records and saving to files.", MsgCodes.info1);
			//save the data in a format file
			String[] data = new String[] {"Number of file partitions for " + saveDestFNamePrefixAra[1] +" data : "+ nameCounter + "\n"};
			fileIO.saveStrings(saveDestFNamePrefixAra[0]+"_format.csv", data);		
			msgObj.dispMessage("StraffSOMMapManager","saveAllExampleMapData","Finished saving all "+exampleName+" map data", MsgCodes.info5);
			return true;
		} else {msgObj.dispMessage("StraffSOMMapManager","saveAllExampleMapData","No "+exampleName+" example data to save. Aborting", MsgCodes.error2); return false;}
	}//saveAllPreProccedMapData


}//class Straff_SOMProspectMapper