package base_SOM_Objects.som_fileIO;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import base_SOM_Objects.*;
import base_SOM_Objects.som_examples.*;
import base_Utils_Objects.*;
import strafford_SOM_PKG.straff_Utils.SOMProjConfigData;

//class that describes the hierarchy of files required for running and analysing a SOM
public class SOMDataLoader implements Runnable {
	public SOMMapManager mapMgr;				//the map these files will use
	//object that manages message displays on screen
	private MessageObject msgObj;
	//manage IO in this object
	private FileIOManager fileIO;

	public SOMProjConfigData projConfigData;			//struct maintaining configuration information for entire project
	
	public final static float nodeDistThresh = 100000.0f;
	
	public int ftrTypeUsedToTrain;
	public boolean useChiSqDist;

	//type of data used to train - 0 : unmodded, 1:std'ized, 2:normalized - should be retrieved from file name
	//private int dataFormat;
	
	public SOMDataLoader(SOMMapManager _mapMgr, SOMProjConfigData _configData) {
		mapMgr = _mapMgr; 
		msgObj = mapMgr.buildMsgObj();
		fileIO = new FileIOManager(msgObj,"SOMDataLoader");
		projConfigData = _configData;
	}

	@Override
	public void run(){
		msgObj.dispMessage("DataLoader","run","Starting data loader", MsgCodes.info5);			
		if(projConfigData.allReqFilesLoaded()){
			msgObj.dispMessage("DataLoader","run","All required files are loaded.", MsgCodes.info1);			
			boolean success = execDataLoad() ;
			mapMgr.setMapDataIsLoaded(success);
			mapMgr.setLoaderRTNSuccess(true);
			mapMgr.setMapImgClrs();
			msgObj.dispMessage("DataLoader","run","Finished data loader : SOM Data Loaded : " + mapMgr.getMapDataIsLoaded()  + " | loader ret code : " +mapMgr.getLoaderRTNSuccess(), MsgCodes.info5 );			
		}
		else {
			mapMgr.setLoaderRTNSuccess(false);
			msgObj.dispMessage("DataLoader","run","Data loader Failed : Required files not all loaded or file IO error ", MsgCodes.error2);
		}
		mapMgr.resetButtonState();
	}//run
	
	//load results from map processing - fnames needs to be modified to handle this
	private boolean execDataLoad(){
		ftrTypeUsedToTrain = mapMgr.getCurrentTrainDataFormat();
		useChiSqDist = mapMgr.getUseChiSqDist();
		//must load jp's and jpg's that were used for this map
		//load map weights for all map nodes
		boolean success = loadSOMWts();	
		//set u-matrix fo all map nodes
		success = loadSOM_nodeDists();
		//load mins and diffs of data used to train map
		success = mapMgr.loadDiffsMins();		
		//load SOM's best matching units for training data - must be after map wts and training data has been loaded
		success = loadSOM_BMUs();
		//if bmus loaded, set bmus for all products
		if (success) {			mapMgr.setAllBMUsFromMap();} 
		else {					msgObj.dispMessage("SOMDataLoader", "execDataLoad", "Unable to match products to map nodes since BMU loading failed", MsgCodes.error2);	}
		//		//load SOM's sorted best matching units for each feature - must be after map wts and training data has been loaded
		return success;
	}//execDataLoad
	
	//return file name from file name and path name
	protected String getFName(String fNameAndPath){
		File file = new File(fNameAndPath);
		String simpleFileName = file.getName();
		return simpleFileName;
	}
		
/////source independent file loading
	//verify file map dimensions agree
	private boolean checkMapDim(String[] tkns, String errorMSG){
		int tmapY = Integer.parseInt(tkns[0]), tmapX = Integer.parseInt(tkns[1]);
		if((tmapY != mapMgr.getMapNodeRows()) || (tmapX != mapMgr.getMapNodeCols())) { 
			msgObj.dispMessage("DataLoader","checkMapDim","!!"+ errorMSG + " dimensions : " + tmapX +","+tmapY+" do not match dimensions of learned weights " + mapMgr.getMapNodeCols() +","+mapMgr.getMapNodeRows()+". Loading aborted.", MsgCodes.error2); 
			return false;} 
		return true;		
	}//checkMapDim
	
	//load map wts from file built by SOM_MAP - need to know format of original data used to train map	
	//Map nodes are similar in format to training examples but scaled based on -their own- data
	//consider actual map data to be feature data, scale map nodes based on min/max feature data seen in wts file
	private boolean loadSOMWts(){//builds mapnodes structure - each map node's weights 
		String wtsFileName = projConfigData.getSOMResFName(projConfigData.wtsIDX);
		msgObj.dispMessage("DataLoader","loadSOMWts","Starting Loading SOM weight data from file : " + getFName(wtsFileName), MsgCodes.info5 );
		mapMgr.initMapNodes();
		mapMgr.clearBMUNodesWithNoExs(ExDataType.Training);//clear structures holding map nodes with and without training examples
		if(wtsFileName.length() < 1){return false;}
		String [] strs= fileIO.loadFileIntoStringAra(wtsFileName, "Loaded wts data file : "+wtsFileName, "Error wts reading file : "+wtsFileName);
		if(strs==null){return false;}
		String[] tkns,ftrNames;
		SOMMapNode dpt;	
		int numEx = 0, mapX=1, mapY=1;//,numWtData = 0;
		Tuple<Integer,Integer> mapLoc;
		//# of training features in each map node
		int numTrainFtrs = 0; 
		
		float[] tmpMapMaxs = null;
		for (int i=0;i<strs.length;++i){//load in data 
			if(i < 2){//first 2 lines are map description : line 0 is map row/col count; map 2 is # ftrs
				tkns = strs[i].replace('%', ' ').trim().split(mapMgr.SOM_FileToken);
				//set map size in nodes
				if(i==0){mapY = Integer.parseInt(tkns[0]);mapMgr.setMapNumRows(mapY);mapX = Integer.parseInt(tkns[1]);mapMgr.setMapNumCols(mapX);	} 
				else {	//# ftrs in map
					numTrainFtrs = Integer.parseInt(tkns[0]);
					ftrNames = new String[numTrainFtrs];
					for(int j=0;j<ftrNames.length;++j){ftrNames[j]=""+j;}			//build temporary names for each feature idx in feature vector					
					//mapMgr.dataHdr = new dataDesc(mapMgr, ftrNames);				//assign numbers to feature name data header 
					tmpMapMaxs = mapMgr.initMapMgrMeanMinVar(ftrNames.length);
				}	
				continue;
			}//if first 2 lines in wts file
			tkns = strs[i].split(mapMgr.SOM_FileToken);
			if(tkns.length < 2){continue;}
			mapLoc = new Tuple<Integer, Integer>((i-2)%mapX, (i-2)/mapX);//map locations in som data are increasing in x first, then y (row major)
			dpt = mapMgr.buildMapNode(mapLoc, tkns);//give each map node its features		
			++numEx;
			mapMgr.addToMapNodes(mapLoc, dpt, tmpMapMaxs, numTrainFtrs);			
		}
		//make sure both unmoddified features and std'ized features are built before determining map mean/var
		mapMgr.finalizeMapNodes(tmpMapMaxs, numTrainFtrs, numEx);		
		msgObj.dispMessage("DataLoader","loadSOMWts","Finished Loading SOM weight data from file : " + getFName(wtsFileName), MsgCodes.info5 );		
		return true;
	}//loadSOMWts	
	
	private void dbgDispFtrAra(float[] ftrData, String exStr) {
		msgObj.dispMessage("DataLoader","dbgDispFtrAra",ftrData.length + " " +exStr + " vals : ", MsgCodes.warning1 );	
		String res = ""+String.format("%.4f",ftrData[0]);
		for(int d=1;d<ftrData.length;++d) {res += ", " + String.format("%.4f", ftrData[d]);		}
		res +="\n";
		msgObj.dispMessage("DataLoader","dbgDispFtrAra",res, MsgCodes.warning1);
	}//dbgDispFtrAra
	
	//verify the best matching units file is as we expect it to be
	private boolean checkBMUHeader(String[] hdrStrAra, String bmFileName) {
		String[] tkns = hdrStrAra[0].replace('%', ' ').trim().split(mapMgr.SOM_FileToken);
		if(!checkMapDim(tkns,"Best Matching Units file " + getFName(bmFileName))){return false;}//otw continue
		tkns = hdrStrAra[1].replace('%', ' ').trim().split(mapMgr.SOM_FileToken);
		int tNumTDat = Integer.parseInt(tkns[0]);
		if(tNumTDat != mapMgr.numTrainData) { //don't forget added emtpy vector
			msgObj.dispMessage("DataLoader","loadSOM_BMUs","!!Best Matching Units file " + getFName(bmFileName) + " # of training examples : " + tNumTDat +" does not match # of training examples in training data " + mapMgr.numTrainData+". Loading aborted.", MsgCodes.error2 ); 
			return false;}					
		return true;
	}//checkBMUHeader
	
	//load best matching units for each training example - has values : idx, mapy, mapx.  Uses file built by som code.  can be verified by comparing actual example distance from each node
	private boolean loadSOM_BMUs(){//modifies existing nodes and datapoints only
		String bmFileName = projConfigData.getSOMResFName(projConfigData.bmuIDX);
		if(bmFileName.length() < 1){return false;}
		//clear out listing of bmus that have training examples already
		mapMgr.clearBMUNodesWithExs(ExDataType.Training);
		msgObj.dispMessage("DataLoader","loadSOM_BMUs","Start Loading BMU File : "+bmFileName, MsgCodes.info5);
		String[] tkns;			
		String[] strs= fileIO.loadFileIntoStringAra(bmFileName, "Loaded best matching unit data file : "+bmFileName, "Error reading best matching unit file : "+bmFileName);			
		if((strs==null) || (strs.length == 0)){return false;}
		if (! checkBMUHeader(strs, bmFileName)) {return false;}
		int numThds =  mapMgr.getNumUsableThreads();
		int bmuListIDX = 0;
		int numMapCols = mapMgr.getMapNodeCols();
		
		HashMap<SOMMapNode, ArrayList<SOMExample>>[] bmusToExs = new HashMap[numThds];
		for (int i=0;i<numThds;++i) {
			bmusToExs[i] = new HashMap<SOMMapNode, ArrayList<SOMExample>>();
		}
		int mapNodeX, mapNodeY, dpIdx;
		SOMExample[] _trainData = mapMgr.getTrainingData();
		int typeOfData = _trainData[0].getTypeVal();
		for (int i=2;i<strs.length;++i){//load in data on all bmu's
			tkns = strs[i].split(mapMgr.SOM_FileToken);
			if(tkns.length < 2){continue;}//shouldn't happen	
			mapNodeX = Integer.parseInt(tkns[2]);
			mapNodeY = Integer.parseInt(tkns[1]);
			dpIdx = Integer.parseInt(tkns[0]);	//datapoint index in training data		
			Tuple<Integer,Integer> mapLoc = new Tuple<Integer, Integer>(mapNodeX,mapNodeY);//map locations in bmu data are in (y,x) order (row major)
			SOMMapNode tmpMapNode = mapMgr.getMapNodeLoc(mapLoc);//mapMgr.MapNodes.get(mapLoc);
			if(null==tmpMapNode){ msgObj.dispMessage("DataLoader","loadSOM_BMUs","!!!!!!!!!Map node stated as best matching unit for training example " + tkns[0] + " not found in map ... somehow. ", MsgCodes.error2); return false;}//catastrophic error shouldn't be possible
			SOMExample tmpDataPt = _trainData[dpIdx];
			if(null==tmpDataPt){ msgObj.dispMessage("DataLoader","loadSOM_BMUs","!!Training Datapoint given by idx in BMU file str tok : " + tkns[0] + " of string : --" + strs[i] + "-- not found in training data. ", MsgCodes.error2); return false;}//catastrophic error shouldn't happen
			//partition bmu and its subsequent child examples to a different list depending on location of bmu
			bmuListIDX = ((mapNodeX * numMapCols) + mapNodeY) % numThds;
			ArrayList<SOMExample> bmuExs = bmusToExs[bmuListIDX].get(tmpMapNode);
			if(bmuExs == null) {bmuExs = new ArrayList<SOMExample>(); bmusToExs[bmuListIDX].put(tmpMapNode, bmuExs);}
			bmuExs.add(tmpDataPt);				
			//debug to verify node row/col order
			//dbgVerifyBMUs(tmpMapNode, tmpDataPt,Integer.parseInt(tkns[1]) ,Integer.parseInt(tkns[2]));
			mapMgr.addExToNodesWithExs(tmpMapNode, ExDataType.Training);
			//mapMgr.nodesWithNoEx.remove(tmpMapNode);
			//msgObj.dispMessage("DataLoader : Tuple "  + mapLoc + " from str @ i-2 = " + (i-2) + " node : " + tmpMapNode.toString());
		}//for each training data point			
		msgObj.dispMessage("DataLoader","loadSOM_BMUs","Built bmus map, now calc dists for examples", MsgCodes.info1);
		boolean doMT = mapMgr.isMTCapable();
		//mapping bmus to training examples
		mapMgr.setTrainDataBMUsRdyToSave(false);
		if (doMT) {
			List<Future<Boolean>> bmuDataBldFtrs;
			List<SOMExBMULoader> bmuDataLoaders = new ArrayList<SOMExBMULoader>();
			////////////////////
			for(int i=0;i<numThds;++i) {bmuDataLoaders.add(new SOMExBMULoader(mapMgr.buildMsgObj(),ftrTypeUsedToTrain,useChiSqDist, typeOfData, bmusToExs[i],i));	}
			try {bmuDataBldFtrs = mapMgr.getTh_Exec().invokeAll(bmuDataLoaders);for(Future<Boolean> f: bmuDataBldFtrs) { f.get(); }} catch (Exception e) { e.printStackTrace(); }		
		} else {		
			//below is the slowest section of this code - to improve performance this part should be multithreaded
			if (useChiSqDist) {
				for (HashMap<SOMMapNode, ArrayList<SOMExample>> bmuToExsMap : bmusToExs) {
					for (SOMMapNode tmpMapNode : bmuToExsMap.keySet()) {
						ArrayList<SOMExample> exs = bmuToExsMap.get(tmpMapNode);
						for(SOMExample ex : exs) {ex.setBMU_ChiSq(tmpMapNode, ftrTypeUsedToTrain);tmpMapNode.addExToBMUs(ex,typeOfData);	}
					}
				}				
			} else {
				for (HashMap<SOMMapNode, ArrayList<SOMExample>> bmuToExsMap : bmusToExs) {
					for (SOMMapNode tmpMapNode : bmuToExsMap.keySet()) {
						ArrayList<SOMExample> exs = bmuToExsMap.get(tmpMapNode);
						for(SOMExample ex : exs) {ex.setBMU(tmpMapNode, ftrTypeUsedToTrain);tmpMapNode.addExToBMUs(ex,typeOfData);	}
					}
				}				
			}
		}//if mt else single thd
		mapMgr.setTrainDataBMUsRdyToSave(true);
		msgObj.dispMessage("DataLoader","loadSOM_BMUs","Start Pruning No-Example list", MsgCodes.info5);
		//remove all examples that have been mapped to
		mapMgr.filterExFromNoEx(ExDataType.Training);
		//for (SOMMapNode tmpMapNode : mapMgr.nodesWithTrainEx) {			mapMgr.nodesWithNoTrainEx.remove(tmpMapNode);		}
		addMappedNodesToEmptyNodes(ExDataType.Training);
		//finalize training examples
		mapMgr.finalizeExMapNodes(ExDataType.Training);
		
		msgObj.dispMessage("DataLoader","loadSOM_BMUs","Finished Loading SOM BMUs from file : " + getFName(bmFileName) + "| Found "+mapMgr.getNumNodesWithBMUExs(ExDataType.Training)+" nodes with training example mappings.", MsgCodes.info5);
		return true;
	}//loadSOM_BMs
	
	public void addMappedNodesToEmptyNodes(ExDataType _type) {
		msgObj.dispMessage("SOMMapManager","addMappedNodesToEmptyNodes","Start assigning map nodes that are not BMUs to any examples to have nearest map node to them as BMU.", MsgCodes.info5);		
		boolean isTorroid = mapMgr.isToroidal();
		float dist,minDist;
	
		HashSet<SOMMapNode> withMap = mapMgr.getNodesWithExOfType(_type),withOutMap = mapMgr.getNodesWithNoExOfType(_type);	
		//set all empty mapnodes to have a label based on the closest mapped node's label
		if (isTorroid) {//minimize in-loop if checks
			for(SOMMapNode node : withOutMap){//node has no label mappings, so need to determine label
				minDist = 1000000;
				SOMMapNode closestMapNode  = node;					//will never be added
				for(SOMMapNode node2 : withMap){		//this is adding a -map- node
					dist = getSqMapDist_torr(node2, node);			//actual map topology dist - need to handle wrapping!
					if (dist < minDist) {minDist = dist;		closestMapNode = node2;}
				}	
				node.addExToBMUs(minDist, closestMapNode);			//adds single closest -map- node we know has a label, or itself if none found
			}
		} else {
			for(SOMMapNode node : withOutMap){//node has no label mappings, so need to determine label
				minDist = 1000000;
				SOMMapNode closestMapNode  = node;					//will never be added
				for(SOMMapNode node2 : withMap){		//this is adding a -map- node
					dist = getSqMapDist_flat(node2, node);			//actual map topology dist - need to handle wrapping!
					if (dist < minDist) {minDist = dist;		closestMapNode = node2;}
				}	
				node.addExToBMUs(minDist, closestMapNode);			//adds single closest -map- node we know has a label, or itself if none found
			}			
		}		
		msgObj.dispMessage("SOMMapManager","addMappedNodesToEmptyNodes","Finished assigning map nodes that are not BMUs to any examples to have nearest map node to them as BMU.", MsgCodes.info5);		
	}//addMappedNodesToEmptyNodes
	
	
	//verify that map node coords are in proper order (row-col vs x-y)
	private void dbgVerifyBMUs(SOMMapNode tmpMapNode, SOMExample tmpDataPt, Integer x, Integer y) {
		//this is alternate node with column-major key
		Tuple<Integer,Integer> mapAltLoc = new Tuple<Integer, Integer>(x,y);//verifying correct row/col order - tmpMapNode should be closer to mapMgr.trainData[dpIdx] than to tmpAltMapNode
		SOMMapNode tmpAltMapNode = mapMgr.getMapNodeLoc(mapAltLoc);//mapMgr.MapNodes.get(mapAltLoc);
		//if using chi-sq dist, must know mapMgr.map_ftrsVar by now
		//double tmpDist =  mapMgr.dpDistFunc(tmpAltMapNode, tmpDataPt);
		double tmpDist;
		if (useChiSqDist) {			tmpDist =  tmpDataPt.getSqDistFromFtrType_ChiSq(tmpAltMapNode, ftrTypeUsedToTrain);  }//mapMgr.dpDistFunc(tmpAltMapNode, tmpDataPt);
		else {						tmpDist =  tmpDataPt.getSqDistFromFtrType(tmpAltMapNode,ftrTypeUsedToTrain);  }//mapMgr.dpDistFunc(tmpAltMapNode, tmpDataPt);
		if(tmpDist < tmpDataPt.get_sqDistToBMU() ) {
			msgObj.dispMessage("DataLoader","loadSOM_BMUs:dbgVerifyBMUs","Somehow bmu calc is incorrect - x/y order of map node location perhaps is swapped? dataPt " + tmpDataPt.OID + " is closer to "+ tmpAltMapNode.OID + " than to predicted BMU : " + tmpMapNode.OID+" : dists : " +tmpDist + " vs. " +tmpDataPt.get_sqDistToBMU(), MsgCodes.warning2);
		}
	}//dbgVerifyBMUs	
		
	//load the u-matrix data used to build the node distance visualization
	private boolean loadSOM_nodeDists() {
		String uMtxBMUFname =  projConfigData.getSOMResFName(projConfigData.umtxIDX);
		msgObj.dispMessage("DataLoader","loadSOM_nodeDists","Start Loading U-Matrix File : "+uMtxBMUFname, MsgCodes.info5);
		if(uMtxBMUFname.length() < 1){return false;}
		String [] strs= fileIO.loadFileIntoStringAra(uMtxBMUFname, "Loaded U Matrix data file : "+uMtxBMUFname, "Error reading U Matrix data file : "+uMtxBMUFname);
		if(strs==null){return false;}
		int numEx = 0, mapX=1, mapY=1,numWtData = 0;
		String[] tkns;
		Tuple<Integer, Integer> mapLoc;
		SOMMapNode dpt;
		int row;
		for (int i=0;i<strs.length;++i){//load in data 
			if(i < 1){//line 0 is map row/col count
				tkns = strs[i].replace('%', ' ').trim().split(mapMgr.SOM_FileToken);
				//set map size in nodes
				mapY = Integer.parseInt(tkns[0]);
				mapX = Integer.parseInt(tkns[1]);	
				//TODO compare values here to set values
				continue;
			}//if first 2 lines in wts file
			tkns = strs[i].trim().split(mapMgr.SOM_FileToken);
			//System.out.println("String : ---"+strs[i]+"---- has length : "+ tkns.length);
			if(tkns.length < 2){continue;}
			row = i-1;
			for (int col=0;col<tkns.length;++col) {
				mapLoc = new Tuple<Integer, Integer>(col, row);//map locations in som data are increasing in x first, then y (row major)
				dpt = mapMgr.getMapNodeLoc(mapLoc);//mapMgr.MapNodes.get(mapLoc);//give each map node its features
				dpt.setUMatDist(Float.parseFloat(tkns[col].trim()));
			}	
		}//
		//update each map node's neighborhood member's weight values
		mapMgr.buildAllMapNodeNeighborhoods();//for(SOMMapNode ex : mapMgr.MapNodes.values()) {	ex.buildNeighborWtVals();	}
		//calculate segments of nodes
		mapMgr.buildSegmentsOnMap();
		msgObj.dispMessage("DataLoader","loadSOM_nodeDists","Finished loading and processing U-Matrix File : "+uMtxBMUFname, MsgCodes.info5);		
		return true;
	}//loadSOM_nodeDists
	
	//returns sq distance between two map locations - needs to handle wrapping if map built torroidally
	public float getSqMapDist_flat(SOMMapNode a, SOMMapNode b){		return (a.mapLoc._SqrDist(b.mapLoc));	}//	
	//returns sq distance between two map locations - needs to handle wrapping if map built torroidally
	public float getSqMapDist_torr(SOMMapNode a, SOMMapNode b){
		float 
			oldXa = a.mapLoc.x - b.mapLoc.x, oldXaSq = oldXa*oldXa,
			newXa = oldXa + mapMgr.getMapWidth(), newXaSq = newXa*newXa,
			oldYa = a.mapLoc.y - b.mapLoc.y, oldYaSq = oldYa*oldYa,
			newYa = oldYa + mapMgr.getMapHeight(), newYaSq = newYa*newYa;
		return (oldXaSq < newXaSq ? oldXaSq : newXaSq ) + (oldYaSq < newYaSq ? oldYaSq : newYaSq);
	}//
	
	
}//dataLoader







