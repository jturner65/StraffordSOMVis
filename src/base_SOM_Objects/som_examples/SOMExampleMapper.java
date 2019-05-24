package base_SOM_Objects.som_examples;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;

import base_SOM_Objects.SOMMapManager;
import base_SOM_Objects.som_utils.SOMProjConfigData;
import base_Utils_Objects.*;

/**
 * this class will manage data handling for all examples of a particular type.  
 * @author john
 */
public abstract class SOMExampleMapper {
		//owning map manager
	public static SOMMapManager mapMgr;
		//message object for logging and to display to screen
	protected MessageObject msgObj;
		//fileIO manager
	protected FileIOManager fileIO;
		//struct maintaining complete project configuration and information from config files - all file name data and building needs to be done by this object
	public static SOMProjConfigData projConfigData;	

		//name of example type
	public final String exampleName;
		//a map keyed by example ID of this specific type of examples
	protected ConcurrentSkipListMap<String, SOMExample> exampleMap;
		//current state of examples
	private int[] stFlags;
	public static final int
		dataIsPreProccedIDX 	= 0,		//raw data has been preprocessed
		dataIsLoadedIDX 		= 1,		//preprocessed data has been loaded
		dataFtrsPreparedIDX 	= 2,		//loaded data features have been pre-procced
		dataFtrsCalcedIDX 		= 3,		//features have been calced
		dataPostFtrsBuiltIDX	= 4;		//post feature calc data has been calculated
	
	public static final int numFlags = 4;
	
		//array of examples actually interacted with by SOM - will be a subset of examples, smaller due to some examples being "bad"
	protected SOMExample[] SOMexampleArray;
		//# of actual examples used by SOM of this type
	protected int numSOMExamples;

	public SOMExampleMapper(SOMMapManager _mapMgr, String _exName) {
		mapMgr = _mapMgr;
		projConfigData = mapMgr.projConfigData;
		exampleName = _exName;
		msgObj = MessageObject.buildMe();
		//fileIO is used to load and save info from/to local files except for the raw data loading, which has its own handling
		fileIO = new FileIOManager(msgObj,"SOMExampleMapper::"+exampleName);

		exampleMap = new ConcurrentSkipListMap<String, SOMExample>();
		initFlags();
	}//ctor
	
	//reset the data held by this example manager
	public final void reset() {
		//faster than rebuilding
		exampleMap.clear();
		SOMexampleArray = new SOMExample[0];
		numSOMExamples = 0;
		//instance-specific code
		reset_Priv();
		//flag settings
		setFlag(dataIsPreProccedIDX, false);
		setFlag(dataIsLoadedIDX, false);
		setFlag(dataFtrsPreparedIDX, false);
		setFlag(dataFtrsCalcedIDX, false);		
	}//reset	
	protected abstract void reset_Priv();
	
	///////////////////////////////
	// prepare and calc feature vectors
	
	/**
	 * pre-condition all examples to prepare for building feature vectors
	 */	
	public void finalizeAllExamples() {
		if(!getFlag(dataIsLoadedIDX)) {
			msgObj.dispMessage("SOMExampleMapper::"+exampleName,"finalizeAllExamples","Unable to finalizeAllExamples " + exampleName+ " examples due to them not having been loaded.  Aborting.", MsgCodes.info1);
			return;
		}
		msgObj.dispMessage("SOMExampleMapper::"+exampleName,"buildFtrVec","Begin finalizing all " +exampleMap.size()+ " " + exampleName+ " examples to prepare them for ftr calc.", MsgCodes.info1);

		//finalize each example - this will aggregate all the jp's that are seen and prepare example for calculating ftr vector
		for (SOMExample ex : exampleMap.values()) {			ex.finalizeBuildBeforeFtrCalc();		}	
		setFlag(dataFtrsPreparedIDX, true);

		msgObj.dispMessage("SOMExampleMapper::"+exampleName,"buildFtrVec","Finished finalizing all " +exampleMap.size()+ " " + exampleName+ " examples to prepare them for ftr calc.", MsgCodes.info1);
	}//finalizeAllExamples()

	/**
	 * build feature vectors for all examples this object maps
	 */
	public void buildFtrVec() {
		if(!getFlag(dataFtrsPreparedIDX)) {
			msgObj.dispMessage("SOMExampleMapper::"+exampleName,"buildFtrVec","Unable to build feature vectors for " + exampleName+ " examples due to them not having been finalized.  Aborting.", MsgCodes.info1);
			return;
		}
		msgObj.dispMessage("SOMExampleMapper::"+exampleName,"buildFtrVec","Begin building feature vectors for " +exampleMap.size()+ " " + exampleName+ " examples.", MsgCodes.info1);
		//instance-specific feature vector building
		buildFtrVec_Priv();		
		setFlag(dataFtrsCalcedIDX, true);
		msgObj.dispMessage("SOMExampleMapper::"+exampleName,"buildFtrVec","Finished building feature vectors for " +exampleMap.size()+ " " + exampleName+ " examples.", MsgCodes.info1);
		
	}//buildFtrVec	
	//code to execute after examples have had ftr prepared, but before features are calculated
	protected abstract void buildFtrVec_Priv();
	
	public void buildPostFtrVecStructs() {
		if(!getFlag(dataFtrsCalcedIDX)) {
			msgObj.dispMessage("SOMExampleMapper::"+exampleName,"buildPostFtrVecStructs","Unable to build Post-feature vector data for " + exampleName+ " examples due to them not having had features calculated.  Aborting.", MsgCodes.info1);
			return;
		}
		msgObj.dispMessage("SOMExampleMapper::"+exampleName,"buildPostFtrVecStructs","Begin building Post-feature vector data for " +exampleMap.size()+ " " + exampleName+ " examples.", MsgCodes.info1);
		//instance-specific feature vector building
		for (SOMExample ex : exampleMap.values()) {	ex.buildPostFeatureVectorStructs();}
		setFlag(dataPostFtrsBuiltIDX, true);
		msgObj.dispMessage("SOMExampleMapper::"+exampleName,"buildPostFtrVecStructs","Finished building Post-feature vectorr data for " +exampleMap.size()+ " " + exampleName+ " examples.", MsgCodes.info1);
		
	}//buildFtrVec	
		
	/////////////////////////////////////////////
	// load and save preprocced data
	public abstract void loadAllPreProccedMapData(String subDir);
	public abstract boolean saveAllPreProccedMapData();
	
	
	////////////////////////////////////
	// build SOM arrays
	
	/**
	 * build the array of examples, and return the array - if validate then only include records that are not "bad"
	 * @param validate whether or not this data should be validated (guaranteed to be reasonable training data - only necessary for data that is going to be used to train)
	 * @return
	 */
	public SOMExample[] buildExampleArray(boolean validate) {		
		if(validate) {
			ArrayList<SOMExample> tmpList = new ArrayList<SOMExample>();
			for (String key : exampleMap.keySet()) {
				//potentially different for every instancing class
				validateAndAddEx(tmpList, exampleMap.get(key));
			}			
			SOMexampleArray = castArray(tmpList);//tmpList.toArray(new SOMExample[0]);
		} 
		else {	SOMexampleArray = noValidateBuildExampleArray();}//exampleMap.values().toArray(new SOMExample[0]);
		numSOMExamples = SOMexampleArray.length;
		return SOMexampleArray;
	}//buildExampleArray		
	protected abstract void validateAndAddEx(ArrayList<SOMExample> tmpList, SOMExample ex);
	protected abstract SOMExample[] castArray(ArrayList<SOMExample> tmpList);
	protected abstract SOMExample[] noValidateBuildExampleArray();
	protected abstract void buildExampleArrayEnd_Priv(boolean validate);

	
	////////////////////////////////
	// add/remove examples	
		//reset acts as initialize
		//add an example, return old example if one existed
	public final SOMExample addExampleToMap(String key, SOMExample ex) {return exampleMap.put(key, ex);	}
		//remove an example by key
	public final SOMExample removeExampleFromMap(String key) {return exampleMap.remove(key);}
		//remove an example - use example's OID
	public final SOMExample removeExampleFromMap(SOMExample ex) {return exampleMap.remove(ex.OID);}
		//return an example by key 
	public final SOMExample getExample(String key) {return exampleMap.get(key);}
		//return the entire example map
	public final ConcurrentSkipListMap<String, SOMExample> getExampleMap(){return exampleMap;}
	
	public Set<String> getExampleKeySet(){return exampleMap.keySet();}
	
	
	public final int getNumSOMExamples() {return numSOMExamples;}	
	public final int getNumMapExamples() {return exampleMap.size();}
	
	////////////////////////
	// state flag handling
	protected final void initFlags() {stFlags = new int[1 + numFlags/32]; for(int i = 0; i<numFlags; ++i){setFlag(i,false);}}
	public void setAllFlags(int[] idxs, boolean val) {for (int idx : idxs) {setFlag(idx, val);}}
	public boolean getFlag(int idx){int bitLoc = 1<<(idx%32);return (stFlags[idx/32] & bitLoc) == bitLoc;}
	public void setFlag(int idx, boolean val) {
		int flIDX = idx/32, mask = 1<<(idx%32);
		stFlags[flIDX] = (val ?  stFlags[flIDX] | mask : stFlags[flIDX] & ~mask);
		switch (idx) {//special actions for each flag
			case dataIsPreProccedIDX 	: {break;}	
			case dataIsLoadedIDX 		: {break;}	
			case dataFtrsPreparedIDX 	: {break;}	
			case dataFtrsCalcedIDX 		: {break;}	
			case dataPostFtrsBuiltIDX 	: {break;}
		}
	}//setFlag
	
	public boolean isDataPreProcced() {return getFlag(dataIsPreProccedIDX);}
	public boolean isDataLoaded() {return getFlag(dataIsLoadedIDX);}
	public boolean isDataFtrsPrepared() {return getFlag(dataFtrsPreparedIDX);}
	public boolean isDataFtrsCalced() {return getFlag(dataFtrsCalcedIDX);}
	
	public void setAllDataLoaded() {setFlag(dataIsLoadedIDX, true);}
	public void setAllDataPreProcced() {setFlag(dataIsPreProccedIDX, true);}


	
	@Override
	public String toString() {
		String res = "Example type name : " + exampleName + " | # of examples : " + exampleMap.size();
		
		return res;
	}
}//class SOMExampleMapper
