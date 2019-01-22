package SOM_Strafford_PKG;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import processing.core.PImage;

//class that describes the hierarchy of files required for running and analysing a SOM
public class SOMDataLoader implements Runnable {
	public SOMMapManager mapMgr;				//the map these files will use
	//manage IO in this object
	private fileIOManager fileIO;

	public SOMProjConfigData projConfigData;			//struct maintaining configuration information for entire project
	
	public final static float nodeDistThresh = 100000.0f;
	
	public int ftrTypeUsedToTrain;
	public boolean useChiSqDist;

	//type of data used to train - 0 : unmodded, 1:std'ized, 2:normalized - should be retrieved from file name
	//private int dataFormat;
	
	public SOMDataLoader(SOMMapManager _mapMgr, SOMProjConfigData _configData) {
		mapMgr = _mapMgr;
		fileIO = new fileIOManager(mapMgr,"SOMDataLoader");
		projConfigData = _configData;
	}

	@Override
	public void run(){
		mapMgr.dispMessage("DataLoader","run","Starting data loader");			
		if(projConfigData.allReqFilesLoaded()){
			mapMgr.dispMessage("DataLoader","run","All required files are loaded.");			
			boolean success = execDataLoad() ;
			mapMgr.setFlag(SOMMapManager.mapDataLoadedIDX,success);
			mapMgr.setFlag(SOMMapManager.loaderRtnIDX,true);
			mapMgr.setMapImgClrs();
			mapMgr.dispMessage("DataLoader","run","Finished data loader : SOM Data Loaded : " + mapMgr.getFlag(SOMMapManager.mapDataLoadedIDX) + " | loader ret code : " +mapMgr.getFlag(SOMMapManager.loaderRtnIDX) );			
		}
		else {
			mapMgr.setFlag(SOMMapManager.loaderRtnIDX,false);
			mapMgr.dispMessage("DataLoader","run","Data loader Failed : Required files not all loaded or file IO error ");
		}
		mapMgr.resetButtonState();
	}//run
	
	//load results from map processing - fnames needs to be modified to handle this
	private boolean execDataLoad(){
		ftrTypeUsedToTrain = mapMgr.getCurrMapDataFrmt();
		useChiSqDist = mapMgr.getUseChiSqDist();
		//must load jp's and jpg's that were used for this map
		//load map weights for all map nodes
		boolean success = loadSOMWts();	
		//set u-matrix fo all map nodes
		success = loadSOM_nodeDists();
		//load mins and diffs of data used to train map
		success = loadDiffsMins();		
		//load SOM's best matching units for training data - must be after map wts and training data has been loaded
		success = loadSOM_BMUs();
		//if bmus loaded, set bmus for all products
		if (success) {			mapMgr.setProductBMUs();		mapMgr.setTestBMUs();} 
		else {					mapMgr.dispMessage("SOMDataLoader", "execDataLoad", "Unable to match products to map nodes since BMU loading failed");	}
		//		//load SOM's sorted best matching units for each feature - must be after map wts and training data has been loaded
		return success;
	}//execDataLoad
	
	//return file name from file name and path name
	protected String getFName(String fNameAndPath){
		File file = new File(fNameAndPath);
		String simpleFileName = file.getName();
		return simpleFileName;
	}
	
	//this will make sure that all scaled features are actually scaled and nonscaled are actually nonscaled
	public boolean loadDiffsMins(){
		String diffsFileName = projConfigData.getSOMMapDiffsFileName(), minsFileName = projConfigData.getSOMMapMinsFileName();
		//load normalizing values for datapoints in weights - differences and mins, used to scale/descale training and map data
		mapMgr.diffsVals = loadCSVSrcDataPoint(diffsFileName);
		if((null==mapMgr.diffsVals) || (mapMgr.diffsVals.length < 1)){mapMgr.dispMessage("DataLoader","condAllData","!!error reading diffsFile : " + diffsFileName); return false;}
		mapMgr.minsVals = loadCSVSrcDataPoint(minsFileName);
		if((null==mapMgr.minsVals)|| (mapMgr.minsVals.length < 1)){mapMgr.dispMessage("DataLoader","condAllData","!!error reading minsFile : " + minsFileName); return false;}	
		return true;
	}//condAllData()
	
	//read file with scaling/min values for Map to convert data back to original feature space - single row of data
	private Float[] loadCSVSrcDataPoint(String fileName){		
		if(fileName.length() < 1){return null;}
		String [] strs= fileIO.loadFileIntoStringAra(fileName, "Loaded data file : "+fileName, "Error reading file : "+fileName);
		if(strs==null){return null;}
		//strs should only be length 1
		if(strs.length > 1){mapMgr.dispMessage("DataLoader","loadCSVSrcDataPoint","error reading file : " + fileName + " String array has more than 1 row.");return null;}		
		String[] tkns = strs[0].split(mapMgr.csvFileToken);
		ArrayList<Float> tmpData = new ArrayList<Float>();
		for(int i =0; i<tkns.length;++i){
			tmpData.add(Float.parseFloat(tkns[i]));
		}
		return tmpData.toArray(new Float[0]);
	}//loadCSVData
		
/////source independent file loading
	//verify file map dimensions agree
	private boolean checkMapDim(String[] tkns, String errorMSG){
		int tmapY = Integer.parseInt(tkns[0]), tmapX = Integer.parseInt(tkns[1]);
		if((tmapY != mapMgr.getMapNodeRows()) || (tmapX != mapMgr.getMapNodeCols())) { 
			mapMgr.dispMessage("DataLoader","checkMapDim","!!"+ errorMSG + " dimensions : " + tmapX +","+tmapY+" do not match dimensions of learned weights " + mapMgr.getMapNodeCols() +","+mapMgr.getMapNodeRows()+". Loading aborted."); 
			return false;} 
		return true;		
	}//checkMapDim
	
	//load map wts from file built by SOM_MAP - need to know format of original data used to train map	
	//Map nodes are similar in format to training examples but scaled based on -their own- data
	//consider actual map data to be feature data, scale map nodes based on min/max feature data seen in wts file
	private boolean loadSOMWts(){//builds mapnodes structure - each map node's weights 
		String wtsFileName = projConfigData.getSOMResFName(projConfigData.wtsIDX);
		mapMgr.MapNodes = new TreeMap<Tuple<Integer,Integer>, SOMMapNode>();
		mapMgr.clearBMUNodesWithNoExs(ExDataType.ProspectTraining);//clear structures holding map nodes with and without training examples
		if(wtsFileName.length() < 1){return false;}
		String [] strs= fileIO.loadFileIntoStringAra(wtsFileName, "Loaded wts data file : "+wtsFileName, "Error wts reading file : "+wtsFileName);
		if(strs==null){return false;}
		String[] tkns,ftrNames;
		SOMMapNode dpt;	
		int numEx = 0, mapX=1, mapY=1,numWtData = 0;
		Tuple<Integer,Integer> mapLoc;
		float[] tmpMapMaxs = null;
		for (int i=0;i<strs.length;++i){//load in data 
			if(i < 2){//first 2 lines are map description : line 0 is map row/col count; map 2 is # ftrs
				tkns = strs[i].replace('%', ' ').trim().split(mapMgr.SOM_FileToken);
				//set map size in nodes
				if(i==0){mapY = Integer.parseInt(tkns[0]);mapMgr.setMapNumRows(mapY);mapX = Integer.parseInt(tkns[1]);mapMgr.setMapNumCols(mapX);	} 
				else {	//# ftrs in map
					mapMgr.numFtrs = Integer.parseInt(tkns[0]);
					ftrNames = new String[mapMgr.numFtrs];
					for(int j=0;j<mapMgr.numFtrs;++j){ftrNames[j]=""+j;}			//build temporary names for each feature idx in feature vector					
					//mapMgr.dataHdr = new dataDesc(mapMgr, ftrNames);				//assign numbers to feature name data header 
					mapMgr.map_ftrsMean = new float[mapMgr.numFtrs];
					tmpMapMaxs = new float[mapMgr.numFtrs];
					mapMgr.map_ftrsMin = new float[mapMgr.numFtrs];
					for(int l=0;l<mapMgr.numFtrs;++l) {mapMgr.map_ftrsMin[l]=10000.0f;}//need to init to big number to get accurate min
					mapMgr.map_ftrsVar = new float[mapMgr.numFtrs];
				}	
				continue;
			}//if first 2 lines in wts file
			tkns = strs[i].split(mapMgr.SOM_FileToken);
			if(tkns.length < 2){continue;}
			mapLoc = new Tuple<Integer, Integer>((i-2)%mapX, (i-2)/mapX);//map locations in som data are increasing in x first, then y (row major)
			dpt = new StraffSOMMapNode(mapMgr, mapLoc, tkns);//give each map node its features
		
			++numEx;
			float[] ftrData = dpt.getFtrs();
			for(int d = 0; d<mapMgr.numFtrs; ++d){
				mapMgr.map_ftrsMean[d] += ftrData[d];
				tmpMapMaxs[d] = (tmpMapMaxs[d] < ftrData[d] ? ftrData[d]  : tmpMapMaxs[d]);
				mapMgr.map_ftrsMin[d] = (mapMgr.map_ftrsMin[d] > ftrData[d] ? ftrData[d]  : mapMgr.map_ftrsMin[d]);
			}
			mapMgr.MapNodes.put(mapLoc, dpt);			
			mapMgr.addExToNodesWithNoExs(dpt, ExDataType.ProspectTraining);//nodesWithNoTrainEx.add(dpt);				//initialize : add all nodes to set, will remove nodes when they get mappings
		}
		//make sure both unmoddified features and std'ized features are built before determining map mean/var
		//need to have all features built to scale features		
		mapMgr.map_ftrsDiffs = new float[mapMgr.numFtrs];
		//initialize array of images to display map of particular feature with
		mapMgr.initMapAras();
		
		for(int d = 0; d<mapMgr.numFtrs; ++d){mapMgr.map_ftrsMean[d] /= 1.0f*numEx;mapMgr.map_ftrsDiffs[d]=tmpMapMaxs[d]-mapMgr.map_ftrsMin[d];}
		//set stdftrs for map nodes and variance calc
		float diff;
		//reset this to manage all map nodes
		mapMgr.initPerJPMapOfNodes();
		float[] ftrData ;
		//for every node, now build standardized features 
		for(Tuple<Integer, Integer> key : mapMgr.MapNodes.keySet()){
			SOMMapNode tmp = mapMgr.MapNodes.get(key);
			tmp.buildStdFtrsMapFromFtrData_MapNode(mapMgr.map_ftrsMin, mapMgr.map_ftrsDiffs);
			//accumulate map ftr moments
			ftrData = tmp.getFtrs();
			for(int d = 0; d<mapMgr.numFtrs; ++d){
				diff = mapMgr.map_ftrsMean[d] - ftrData[d];
				mapMgr.map_ftrsVar[d] += diff*diff;
			}
			mapMgr.setMapNodeFtrStr(tmp);
		}
		for(int d = 0; d<mapMgr.numFtrs; ++d){mapMgr.map_ftrsVar[d] /= 1.0f*numEx;}
		mapMgr.dispMessage("DataLoader","loadSOMWts","Finished Loading SOM weight data from file : " + getFName(wtsFileName) );
		
		return true;
	}//loadSOMWts	
	
	private void dbgDispFtrAra(float[] ftrData, String exStr) {
		mapMgr.dispMessage("DataLoader","dbgDispFtrAra",ftrData.length + " " +exStr + " vals : ");	
		String res = ""+String.format("%.4f",ftrData[0]);
		for(int d=1;d<ftrData.length;++d) {res += ", " + String.format("%.4f", ftrData[d]);		}
		res +="\n";
		mapMgr.dispMessage("DataLoader","dbgDispFtrAra",res);
	}//dbgDispFtrAra

	//load into multiple arrays for multi-threaded processing
	public String[][] loadFileIntoStringAra_MT(String fileName, String dispYesStr, String dispNoStr, int numHdrLines, int numThds) {
		try {return _loadFileIntoStringAra_MT(fileName, dispYesStr, dispNoStr, numHdrLines, numThds);} 
		catch (Exception e) {e.printStackTrace(); } 
		return new String[0][];
	}
	//load files into multiple arrays for multi-threaded processing
	private String[][] _loadFileIntoStringAra_MT(String fileName, String dispYesStr, String dispNoStr, int numHdrLines, int numThds) throws IOException {		
		FileInputStream inputStream = null;
		Scanner sc = null;
		List<String>[] lines = new ArrayList[numThds];
		for (int i=0;i<numThds;++i) {lines[i]=new ArrayList<String>();	}
		String[][] res = new String[numThds+1][];
		String[] hdrRes = new String[numHdrLines];
		int idx = 0, count = 0;
		try {
		    inputStream = new FileInputStream(fileName);
		    sc = new Scanner(inputStream);
		    for(int i=0;i<numHdrLines;++i) {    	hdrRes[i]=sc.nextLine();   }		    
		    while (sc.hasNextLine()) {
		    	lines[idx].add(sc.nextLine()); 
		    	idx = (idx + 1)%numThds;
		    	++count;
		    }
		    //Scanner suppresses exceptions
		    if (sc.ioException() != null) { throw sc.ioException(); }
		    mapMgr.dispMessage("DataLoader", "_loadFileIntoStringAra_MT",dispYesStr+"\tLength : " +  count + " distributed into "+lines.length+" arrays.");
		    for (int i=0;i<lines.length;++i) {res[i] = lines[i].toArray(new String[0]);	 }
		    res[res.length-1]=hdrRes;
		} catch (Exception e) {	
			e.printStackTrace();
			mapMgr.dispMessage("DataLoader", "_loadFileIntoStringAra_MT","!!"+dispNoStr);
			res= new String[0][];
		} 
		finally {
		    if (inputStream != null) {inputStream.close();		    }
		    if (sc != null) { sc.close();		    }
		}
		return res;
	}//_loadFileIntoStringAra_MT
	
	//verify the best matching units file is as we expect it to be
	private boolean checkBMUHeader(String[] hdrStrAra, String bmFileName) {
		String[] tkns = hdrStrAra[0].replace('%', ' ').trim().split(mapMgr.SOM_FileToken);
		if(!checkMapDim(tkns,"Best Matching Units file " + getFName(bmFileName))){return false;}//otw continue
		tkns = hdrStrAra[1].replace('%', ' ').trim().split(mapMgr.SOM_FileToken);
		int tNumTDat = Integer.parseInt(tkns[0]);
		if(tNumTDat != mapMgr.numTrainData) { //don't forget added emtpy vector
			mapMgr.dispMessage("DataLoader","loadSOM_BMUs","!!Best Matching Units file " + getFName(bmFileName) + " # of training examples : " + tNumTDat +" does not match # of training examples in training data " + mapMgr.numTrainData+". Loading aborted." ); 
			return false;}					
		return true;
	}//checkBMUHeader
	
	//load best matching units for each training example - has values : idx, mapy, mapx.  Uses file built by som code.  can be verified by comparing actual example distance from each node
	private boolean loadSOM_BMUs(){//modifies existing nodes and datapoints only
		String bmFileName = projConfigData.getSOMResFName(projConfigData.bmuIDX);
		if(bmFileName.length() < 1){return false;}
		mapMgr.clearBMUNodesWithExs(ExDataType.ProspectTraining);
		mapMgr.dispMessage("DataLoader","loadSOM_BMUs","Start Loading BMU File : "+bmFileName);
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
		for (int i=2;i<strs.length;++i){//load in data on all bmu's
			tkns = strs[i].split(mapMgr.SOM_FileToken);
			if(tkns.length < 2){continue;}//shouldn't happen	
			mapNodeX = Integer.parseInt(tkns[2]);
			mapNodeY = Integer.parseInt(tkns[1]);
			dpIdx = Integer.parseInt(tkns[0]);	//datapoint index in training data		
			Tuple<Integer,Integer> mapLoc = new Tuple<Integer, Integer>(mapNodeX,mapNodeY);//map locations in bmu data are in (y,x) order (row major)
			SOMMapNode tmpMapNode = mapMgr.MapNodes.get(mapLoc);
			if(null==tmpMapNode){ mapMgr.dispMessage("DataLoader","loadSOM_BMUs","!!!!!!!!!Map node stated as best matching unit for training example " + tkns[0] + " not found in map ... somehow. "); return false;}//catastrophic error shouldn't be possible
			SOMExample tmpDataPt = mapMgr.trainData[dpIdx];
			if(null==tmpDataPt){ mapMgr.dispMessage("DataLoader","loadSOM_BMUs","!!Training Datapoint given by idx in BMU file str tok : " + tkns[0] + " of string : --" + strs[i] + "-- not found in training data. "); return false;}//catastrophic error shouldn't happen
			//partition bmu and its subsequent child examples to a different list depending on location of bmu
			bmuListIDX = ((mapNodeX * numMapCols) + mapNodeY) % numThds;
			ArrayList<SOMExample> bmuExs = bmusToExs[bmuListIDX].get(tmpMapNode);
			if(bmuExs == null) {bmuExs = new ArrayList<SOMExample>(); bmusToExs[bmuListIDX].put(tmpMapNode, bmuExs);}
			bmuExs.add(tmpDataPt);				
			//debug to verify node row/col order
			//dbgVerifyBMUs(tmpMapNode, tmpDataPt,Integer.parseInt(tkns[1]) ,Integer.parseInt(tkns[2]));
			mapMgr.addExToNodesWithExs(tmpMapNode, ExDataType.ProspectTraining);
			//mapMgr.nodesWithNoEx.remove(tmpMapNode);
			//mapMgr.dispMessage("DataLoader : Tuple "  + mapLoc + " from str @ i-2 = " + (i-2) + " node : " + tmpMapNode.toString());
		}//for each training data point			
		mapMgr.dispMessage("DataLoader","loadSOM_BMUs","Built bmus map, now calc dists for examples");
		boolean doMT = mapMgr.isMTCapable();
		if (doMT) {
			List<Future<Boolean>> bmuDataBldFtrs;
			List<straffBMULoader> bmuDataLoaders = new ArrayList<straffBMULoader>();
			////////////////////
			for(int i=0;i<numThds;++i) {
				bmuDataLoaders.add(new straffBMULoader(mapMgr,ftrTypeUsedToTrain,useChiSqDist, bmusToExs[i],i));
			}
			try {bmuDataBldFtrs = mapMgr.getTh_Exec().invokeAll(bmuDataLoaders);for(Future<Boolean> f: bmuDataBldFtrs) { f.get(); }} catch (Exception e) { e.printStackTrace(); }		
		} else {		
			//below is the slowest section of this code - to improve performance this part should be multithreaded
			if (useChiSqDist) {
				for (HashMap<SOMMapNode, ArrayList<SOMExample>> bmuToExsMap : bmusToExs) {
					for (SOMMapNode tmpMapNode : bmuToExsMap.keySet()) {
						ArrayList<SOMExample> exs = bmuToExsMap.get(tmpMapNode);
						for(SOMExample ex : exs) {ex.setBMU_ChiSq(tmpMapNode, ftrTypeUsedToTrain);tmpMapNode.addExToBMUs(ex);	}
					}
				}
				
			} else {
				for (HashMap<SOMMapNode, ArrayList<SOMExample>> bmuToExsMap : bmusToExs) {
					for (SOMMapNode tmpMapNode : bmuToExsMap.keySet()) {
						ArrayList<SOMExample> exs = bmuToExsMap.get(tmpMapNode);
						for(SOMExample ex : exs) {ex.setBMU(tmpMapNode, ftrTypeUsedToTrain);tmpMapNode.addExToBMUs(ex);	}
					}
				}				
			}
		}//if mt else single thd
		
		mapMgr.dispMessage("DataLoader","loadSOM_BMUs","Start Pruning No-Example list");
		//remove all examples that have been mapped to
		mapMgr.filterExFromNoEx(ExDataType.ProspectTraining);
		//for (SOMMapNode tmpMapNode : mapMgr.nodesWithTrainEx) {			mapMgr.nodesWithNoTrainEx.remove(tmpMapNode);		}
		addMappedNodesToEmptyNodes(ExDataType.ProspectTraining);
		//finalize training examples
		mapMgr.finalizeExMapNodes(ExDataType.ProspectTraining);
		
		mapMgr.dispMessage("DataLoader","loadSOM_BMUs","Finished Loading SOM BMUs from file : " + getFName(bmFileName) + "| Found "+mapMgr.getNumNodesWithBMUExs(ExDataType.ProspectTraining)+" nodes with training example mappings.");
		return true;
	}//loadSOM_BMs
	
	public void addMappedNodesToEmptyNodes(ExDataType _type) {
		mapMgr.dispMessage("SOMMapManager","addMappedNodesToEmptyNodes","Start assigning map nodes that are not BMUs to any examples to have nearest map node to them as BMU.");		
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
		mapMgr.dispMessage("SOMMapManager","addMappedNodesToEmptyNodes","Finished assigning map nodes that are not BMUs to any examples to have nearest map node to them as BMU.");		
	}//addMappedNodesToEmptyNodes
	
	
	//verify that map node coords are in proper order (row-col vs x-y)
	private void dbgVerifyBMUs(SOMMapNode tmpMapNode, SOMExample tmpDataPt, Integer x, Integer y) {
		//this is alternate node with column-major key
		Tuple<Integer,Integer> mapAltLoc = new Tuple<Integer, Integer>(x,y);//verifying correct row/col order - tmpMapNode should be closer to mapMgr.trainData[dpIdx] than to tmpAltMapNode
		SOMMapNode tmpAltMapNode = mapMgr.MapNodes.get(mapAltLoc);
		//if using chi-sq dist, must know mapMgr.map_ftrsVar by now
		//double tmpDist =  mapMgr.dpDistFunc(tmpAltMapNode, tmpDataPt);
		double tmpDist;
		if (useChiSqDist) {			tmpDist =  tmpDataPt.getSqDistFromFtrType_ChiSq(tmpAltMapNode, ftrTypeUsedToTrain);  }//mapMgr.dpDistFunc(tmpAltMapNode, tmpDataPt);
		else {						tmpDist =  tmpDataPt.getSqDistFromFtrType(tmpAltMapNode,ftrTypeUsedToTrain);  }//mapMgr.dpDistFunc(tmpAltMapNode, tmpDataPt);
		if(tmpDist < tmpDataPt._sqDistToBMU ) {
			mapMgr.dispMessage("DataLoader","loadSOM_BMUs:dbgVerifyBMUs","Somehow bmu calc is incorrect - x/y order of map node location perhaps is swapped? dataPt " + tmpDataPt.OID + " is closer to "+ tmpAltMapNode.OID + " than to predicted BMU : " + tmpMapNode.OID+" : dists : " +tmpDist + " vs. " +tmpDataPt._sqDistToBMU);
		}
	}//dbgVerifyBMUs	
		
	//load the u-matrix data used to build the node distance visualization
	private boolean loadSOM_nodeDists() {
		String uMtxBMUFname =  projConfigData.getSOMResFName(projConfigData.umtxIDX);
		mapMgr.dispMessage("DataLoader","loadSOM_nodeDists","Start Loading U-Matrix File : "+uMtxBMUFname);
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
				dpt = mapMgr.MapNodes.get(mapLoc);//give each map node its features
				dpt.setUMatDist(Float.parseFloat(tkns[col].trim()));
			}	
		}//
		//update each map node's neighborhood member's weight values
		for(SOMMapNode ex : mapMgr.MapNodes.values()) {	ex.buildNeighborWtVals();	}
		//calculate segments of nodes
		mapMgr.buildSegmentsOnMap();
		mapMgr.dispMessage("DataLoader","loadSOM_nodeDists","Finished loading and processing U-Matrix File : "+uMtxBMUFname);		
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

//load best matching units for each provided example - 
class straffBMULoader implements Callable<Boolean>{
	SOMMapManager mapMgr;
	int thdIDX;
	boolean useChiSqDist;
	int ftrTypeUsedToTrain;
	HashMap<SOMMapNode, ArrayList<SOMExample>> bmusToExmpl;
	public straffBMULoader(SOMMapManager _mapMgr, int _ftrTypeUsedToTrain, boolean _useChiSqDist, HashMap<SOMMapNode, ArrayList<SOMExample>> _bmusToExmpl,int _thdIDX) {
		mapMgr = _mapMgr;
		ftrTypeUsedToTrain = _ftrTypeUsedToTrain;
		useChiSqDist =_useChiSqDist;
		thdIDX= _thdIDX;	
		bmusToExmpl = _bmusToExmpl;
		int numExs = 0;
		for (SOMMapNode tmpMapNode : bmusToExmpl.keySet()) {
			ArrayList<SOMExample> exs = bmusToExmpl.get(tmpMapNode);
			numExs += exs.size();
		}		
		mapMgr.dispMessage("straffBMULoader","ctor : thd_idx : "+thdIDX, "# of bmus to proc : " +  bmusToExmpl.size() + " # exs : " + numExs);
	}//ctor

	@Override
	public Boolean call() throws Exception {
		if (useChiSqDist) {		
			for (SOMMapNode tmpMapNode : bmusToExmpl.keySet()) {
				ArrayList<SOMExample> exs = bmusToExmpl.get(tmpMapNode);
				for(SOMExample ex : exs) {ex.setBMU_ChiSq(tmpMapNode, ftrTypeUsedToTrain);tmpMapNode.addExToBMUs(ex);	}
			}		
		} else {		
			for (SOMMapNode tmpMapNode : bmusToExmpl.keySet()) {
				ArrayList<SOMExample> exs = bmusToExmpl.get(tmpMapNode);
				for(SOMExample ex : exs) {ex.setBMU(tmpMapNode, ftrTypeUsedToTrain); tmpMapNode.addExToBMUs(ex);	}
			}
		}	
		return true;
	}//run	
}//straffBMULoader

//this class will find the bmus for the passed dataset - the passed reference is to 
//the entire dataset, each instance of this callable will process a subset of this dataset
class mapTestDataToBMUs implements Callable<Boolean>{
	SOMMapManager mapMgr;
	int stIdx, endIdx, curMapFtrType, thdIDX;
	//calculate the exclusionary feature distance(only measure distance from map via features that the node has non-zero values in)
	private boolean useChiSqDist;
	
	int ftrTypeUsedToTrain;
	SOMExample[] exs;
	String ftrTypeDesc;

	public mapTestDataToBMUs(SOMMapManager _mapMgr, int _stProdIDX, int _endProdIDX, SOMExample[] _exs, int _thdIDX, boolean _useChiSqDist) {
		mapMgr = _mapMgr;
		stIdx = _stProdIDX;
		endIdx = _endProdIDX;
		thdIDX= _thdIDX;
		exs=_exs;
		curMapFtrType = mapMgr.getCurrMapDataFrmt();
		ftrTypeDesc = mapMgr.getDataDescFromCurFtrType();
		useChiSqDist = _useChiSqDist;		
	}	
	@Override
	public Boolean call() throws Exception {
		//for every example find closest map node
		//the function call at the end is ignored by product examples
		mapMgr.dispMessage("mapTestDataToBMUs", "Run Thread : " +thdIDX, "Starting Test Data to BMU mapping using " + ftrTypeDesc + " Features and including all features in distance.");
		if (useChiSqDist) {		for (int i=stIdx;i<endIdx;++i) {exs[i].findBMUFromNodes_ChiSq_Excl(mapMgr.MapNodes, curMapFtrType);}} 
		else {					for (int i=stIdx;i<endIdx;++i) {exs[i].findBMUFromNodes_Excl(mapMgr.MapNodes,  curMapFtrType); }}		
		mapMgr.dispMessage("mapTestDataToBMUs", "Run Thread : " +thdIDX, "Finished Test Data to BMU mapping");
		
		return true;
	}		
}//mapTestToBMUs	

//maps products to all map nodes, not just bmu
class mapProductDataToBMUs implements Callable<Boolean>{
	SOMMapManager mapMgr;
	int stIdx, endIdx, curMapFtrType, thdIDX;
	//calculate the exclusionary feature distance(only measure distance from map via features that the node has non-zero values in)
	private boolean useChiSqDist;
	
	int ftrTypeUsedToTrain;
	ProductExample[] exs;
	String ftrTypeDesc;

	public mapProductDataToBMUs(SOMMapManager _mapMgr, int _stProdIDX, int _endProdIDX, ProductExample[] _exs, int _thdIDX, boolean _useChiSqDist) {
		mapMgr = _mapMgr;
		stIdx = _stProdIDX;
		endIdx = _endProdIDX;
		thdIDX= _thdIDX;
		exs=_exs;
		curMapFtrType = mapMgr.getCurrMapDataFrmt();
		ftrTypeDesc = mapMgr.getDataDescFromCurFtrType();
		useChiSqDist = _useChiSqDist;
	}	
	@Override
	public Boolean call() throws Exception {
		//for every example find closest map node
		//the function call at the end is ignored by product examples
		mapMgr.dispMessage("mapTestDataToBMUs", "Run Thread : " +thdIDX, "Starting Product data to BMU mapping using " + ftrTypeDesc + " Features and both including and excluding unshared features in distance.");
		TreeMap<Double, ArrayList<SOMMapNode>> mapNodesByDist;
		if (useChiSqDist) {	
			for (int i=stIdx;i<endIdx;++i) {
				mapNodesByDist = exs[i].findBMUFromNodes_ChiSq_Excl(mapMgr.MapNodes, curMapFtrType); 
				exs[i].setMapNodesStruct(ProductExample.SharedFtrsIDX, mapNodesByDist);
				mapNodesByDist = exs[i].findBMUFromNodes_ChiSq(mapMgr.MapNodes, curMapFtrType); 
				exs[i].setMapNodesStruct(ProductExample.AllFtrsIDX, mapNodesByDist);  
			}
		} else {							
			for (int i=stIdx;i<endIdx;++i) {
				mapNodesByDist = exs[i].findBMUFromNodes_Excl(mapMgr.MapNodes,  curMapFtrType); 
				exs[i].setMapNodesStruct(ProductExample.SharedFtrsIDX, mapNodesByDist);
				mapNodesByDist = exs[i].findBMUFromNodes(mapMgr.MapNodes,  curMapFtrType); 
				exs[i].setMapNodesStruct(ProductExample.AllFtrsIDX, mapNodesByDist);
			}
		}					
		mapMgr.dispMessage("mapTestDataToBMUs", "Run Thread : " +thdIDX, "Finished Product data to BMU mapping");
		
		return true;
	}		
}//mapTestToBMUs
	
//this will build a single image of the map based on ftr data
class straffMapVisImgBuilder implements Callable<Boolean>{
	SOMMapManager mapMgr;
	int mapX, mapY;
	int xSt, xEnd, ySt, yEnd;
	int imgW;
	//type of features to use to build vis, based on type used to train map (unmodified, stdftrs, normftrs)
	int ftrType;
	float mapScaleVal, sclMultXPerPxl, sclMultYPerPxl;
	TreeMap<Tuple<Integer,Integer>, SOMMapNode> MapNodes;
	private PImage[] mapLocClrImg;
	public straffMapVisImgBuilder(SOMMapManager _mapMgr, PImage[] _mapLocClrImg, int[] _xVals, int[] _yVals,  float _mapScaleVal) {
		mapMgr = _mapMgr;
		MapNodes = mapMgr.MapNodes;
		ftrType = mapMgr.getCurrMapDataFrmt();
		mapLocClrImg = _mapLocClrImg;
		mapX = mapMgr.getMapNodeCols();
		xSt = _xVals[0];
		xEnd = _xVals[1];
		if(mapLocClrImg.length == 0) {		imgW = 0;} 
		else {								imgW = mapLocClrImg[0].width;}
		mapY = mapMgr.getMapNodeRows();
		ySt = _yVals[0];
		yEnd = _yVals[1];

		mapScaleVal = _mapScaleVal;
		sclMultXPerPxl = mapScaleVal * mapMgr.getNodePerPxlCol();
		sclMultYPerPxl = mapScaleVal * mapMgr.getNodePerPxlRow();
	}//ctor
	
	public float[] getMapNodeLocFromPxlLoc(float mapPxlX, float mapPxlY){	return new float[]{(mapPxlX * sclMultXPerPxl) - .5f, (mapPxlY * sclMultYPerPxl) - .5f};}	
		
	//get treemap of features that interpolates between two maps of features
	private TreeMap<Integer, Float> interpTreeMap(TreeMap<Integer, Float> a, TreeMap<Integer, Float> b, float t, float mult){
		TreeMap<Integer, Float> res = new TreeMap<Integer, Float>();
		float Onemt = 1.0f-t;
		if(mult==1.0) {
			//first go through all a values
			for(Integer key : a.keySet()) {
				Float aVal = a.get(key), bVal = b.get(key);
				if(bVal == null) {bVal = 0.0f;}
				res.put(key, (aVal*Onemt) + (bVal*t));			
			}
			//next all b values
			for(Integer key : b.keySet()) {
				Float aVal = a.get(key);
				if(aVal == null) {aVal = 0.0f;} else {continue;}		//if aVal is not null then calced already
				Float bVal = b.get(key);
				res.put(key, (aVal*Onemt) + (bVal*t));			
			}
		} else {//scale by mult - precomputes color values
			float m1t = mult*Onemt, mt = mult*t;
			//first go through all a values
			for(Integer key : a.keySet()) {
				Float aVal = a.get(key), bVal = b.get(key);
				if(bVal == null) {bVal = 0.0f;}
				res.put(key, (aVal*m1t) + (bVal*mt));			
			}
			//next all b values
			for(Integer key : b.keySet()) {
				Float aVal = a.get(key);
				if(aVal == null) {aVal = 0.0f;} else {continue;}		//if aVal is not null then calced already
				Float bVal = b.get(key);
				res.put(key, (aVal*m1t) + (bVal*mt));			
			}			
		}		
		return res;
	}//interpolate between 2 tree maps
	
	//return interpolated feature vector on map at location given by x,y, where x,y is float location of map using mapnodes as integral locations
	private TreeMap<Integer, Float> getInterpFtrs(float x, float y){
		int xInt = (int) Math.floor(x+mapX)%mapX, yInt = (int) Math.floor(y+mapY)%mapY, xIntp1 = (xInt+1)%mapX, yIntp1 = (yInt+1)%mapY;		//assume torroidal map		
		float xInterp = (x+1) %1, yInterp = (y+1) %1;
		//always compare standardized feature data in test/train data to standardized feature data in map
		TreeMap<Integer, Float> LowXLowYFtrs = MapNodes.get(new Tuple<Integer, Integer>(xInt,yInt)).getCurrentFtrMap(ftrType), 
				LowXHiYFtrs= MapNodes.get(new Tuple<Integer, Integer>(xInt,yIntp1)).getCurrentFtrMap(ftrType),
				HiXLowYFtrs= MapNodes.get(new Tuple<Integer, Integer>(xIntp1,yInt)).getCurrentFtrMap(ftrType),  
				HiXHiYFtrs= MapNodes.get(new Tuple<Integer, Integer>(xIntp1,yIntp1)).getCurrentFtrMap(ftrType);
		try{
			TreeMap<Integer, Float> ftrs = interpTreeMap(interpTreeMap(LowXLowYFtrs, LowXHiYFtrs,yInterp,1.0f),interpTreeMap(HiXLowYFtrs, HiXHiYFtrs,yInterp,1.0f),xInterp,255.0f);	
			return ftrs;
		} catch (Exception e){
			mapMgr.dispMessage("mySOMMapUIWin","getInterpFtrs","Exception triggered in mySOMMapUIWin::getInterpFtrs : \n"+e.toString() + "\n\tMessage : "+e.getMessage() );
			return null;
		}		
	}//getInterpFtrs	
	
	private int getDataClrFromFtrVec(TreeMap<Integer, Float> ftrMap, Integer jpIDX) {
		Float ftrVal = ftrMap.get(jpIDX);
		if(ftrVal == null) {return 0;}
		int ftr = Math.round(ftrVal);
		int clrVal = ((ftr & 0xff) << 16) + ((ftr & 0xff) << 8) + (ftr & 0xff);
		return clrVal;
	}//getDataClrFromFtrVec	
	
	@Override
	public Boolean call() throws Exception {
		//build portion of every map in each thread-  this speeds up time consuming interpolation between neighboring nodes for each location
		float[] c;
		for(int y = ySt; y<yEnd; ++y){
			int yCol = y * imgW;
			for(int x = xSt; x < xEnd; ++x){
				c = getMapNodeLocFromPxlLoc(x, y);
				TreeMap<Integer, Float> ftrs = getInterpFtrs(c[0],c[1]);
				//for (int i=0;i<mapLocClrImg.length;++i) {	mapLocClrImg[i].pixels[x+yCol] = getDataClrFromFtrVec(ftrs, i);}
				//only access map that the interpolated vector has values for
				for (Integer jp : ftrs.keySet()) {mapLocClrImg[jp].pixels[x+yCol] = getDataClrFromFtrVec(ftrs, jp);}
			}
		}
		return true;
	}	
}//straffMapVisImgBuilder

//this class will load the pre-procced csv data into the prospect data structure owned by the SOMMapData object
class straffCSVDataLoader implements Callable<Boolean>{
	public SOMMapManager mapMgr;
	private String fileName, dispYesStr, dispNoStr;
	private int thdIDX;
	
	public straffCSVDataLoader(SOMMapManager _mapMgr, int _thdIDX, String _fileName, String _yStr, String _nStr) {	mapMgr=_mapMgr;thdIDX=_thdIDX;fileName=_fileName;dispYesStr=_yStr;dispNoStr=_nStr;}	
	private String[] loadFileIntoStringAra() {try {return _loadFileIntoStringAra();} catch (Exception e) {e.printStackTrace(); } return new String[0];}
	//stream read the csv file and build the data objects
	private String[] _loadFileIntoStringAra() throws IOException {		
		FileInputStream inputStream = null;
		Scanner sc = null;
		List<String> lines = new ArrayList<String>();
		String[] res = null;
	    //int line = 1, badEntries = 0;
		try {
		    inputStream = new FileInputStream(fileName);
		    sc = new Scanner(inputStream);
		    while (sc.hasNextLine()) {lines.add(sc.nextLine()); }
		    //Scanner suppresses exceptions
		    if (sc.ioException() != null) { throw sc.ioException(); }
		    mapMgr.dispMessage("straffCSVDataLoader", "loadFileIntoStringAra",dispYesStr+"\tLength : " +  lines.size());
		    res = lines.toArray(new String[0]);		    
		} catch (Exception e) {	
			e.printStackTrace();
			mapMgr.dispMessage("straffCSVDataLoader", "loadFileIntoStringAra","!!"+dispNoStr);
			res= new String[0];
		} 
		finally {
		    if (inputStream != null) {inputStream.close();		    }
		    if (sc != null) { sc.close();		    }
		}
		return res;
	}//loadFileContents

	@Override
	public Boolean call() throws Exception {	
		String[] csvLoadRes = loadFileIntoStringAra();
		//ignore first entry - header
		for (int j=1;j<csvLoadRes.length; ++j) {
			String str = csvLoadRes[j];
			int pos = str.indexOf(',');
			String oid = str.substring(0, pos);
			ProspectExample ex = new ProspectExample(mapMgr, oid, str);
			ProspectExample oldEx = mapMgr.putInProspectMap(ex);//mapMgr.prospectMap.put(ex.OID, ex);	
			if(oldEx != null) {mapMgr.dispMessage("straffCSVDataLoader", "call thd : " + thdIDX, "ERROR : "+thdIDX+" : Attempt to add duplicate record to prospectMap w/OID : " + oid);	}
		}		
		return true;
	}	
}//class straffCSVDataLoader

//save all Strafford training/testing data to appropriate format for SOM
class straffDataWriter implements Callable<Boolean>{
	//public SOM_StraffordMain pa;
	private SOMMapManager mapData;	
	private int dataFrmt;
	private int dataSavedIDX;			//idx in flags array of SOMMapData object to denote what data was saved
	private SOMExample[] exAra;
	private int numFtrs,numSmpls;
	private String savFileFrmt;
	private String fileName;
	//manage IO in this object
	private fileIOManager fileIO;
	
	public straffDataWriter(SOMMapManager _mapData, int _dataFrmt, int _dataSavedIDX, String _fileName, String _savFileFrmt, SOMExample[] _exAra) {
		mapData = _mapData;
		dataFrmt = _dataFrmt;		//either unmodified, standardized or normalized -> 0,1,2
		exAra = _exAra;
		numFtrs = mapData.numFtrs;
		numSmpls = exAra.length;
		savFileFrmt = _savFileFrmt;
		fileName = _fileName;
		dataSavedIDX = _dataSavedIDX;
		fileIO = new fileIOManager(mapData, "straffDataWriter");
	}//ctor

	//build LRN file header
	private String[] buildInitLRN() {
		String[] outStrings = new String[numSmpls + 4];
		//# of data points
		outStrings[0]="% "+numSmpls;
		//# of features per data point +1
		outStrings[1]="% "+numFtrs;
		//9 + 1's * smplDim
		String str1="% 9", str2 ="% Key";
		for(int i=0; i< numFtrs; ++i) {
			str1 +=" 1";
			str2 +=" c"+(i+1);
		}
		outStrings[2]=str1;
		//'Key' + c{i} where i is 1->smplDim
		outStrings[3]=str2;		
		return outStrings;
	}//buildInitLRN
	
	//write file to save all data samples in appropriate format for 
	private void saveLRNData() {
		String[] outStrings = buildInitLRN();
		int strIDX = 4;
		for (int i=0;i<exAra.length; ++i) {outStrings[i+strIDX]=exAra[i].toLRNString(dataFrmt, " ");	}
		fileIO.saveStrings(fileName,outStrings);		
		mapData.dispMessage("straffDataWriter","saveLRNData","Finished saving .lrn file with " + outStrings.length+ " elements to file : "+ fileName);			
	}//save lrn train data
	
	//save data in csv format
	private void saveCSVData() {
		//use buildInitLRN for test and train
		String[] outStrings = buildInitLRN();
		int strIDX = 4;
		for (int i=0;i<exAra.length; ++i) {outStrings[i+strIDX]=exAra[i].toCSVString(dataFrmt);	}
		fileIO.saveStrings(fileName,outStrings);		
		mapData.dispMessage("straffDataWriter","saveCSVData","Finished saving .csv file with " + outStrings.length+ " elements to file : "+ fileName);			
	}//save csv test data
	
	//save data in SVM record form - each record is like a map/dict -> idx: val pair.  designed for sparse data
	private void saveSVMData() {
		//need to save a vector to determine the 
		String[] outStrings = new String[numSmpls];
		for (int i=0;i<exAra.length; ++i) {outStrings[i]=exAra[i].toSVMString(dataFrmt);	}
		fileIO.saveStrings(fileName,outStrings);		
		mapData.dispMessage("straffDataWriter","saveSVMData","Finished saving .svm (sparse) file with " + outStrings.length+ " elements to file : "+ fileName);			
	}

	//write all sphere data to appropriate files
	@Override
	public Boolean call() {		
		//save to lrnFileName - build lrn file
		//4 extra lines that describe dense .lrn file - started with '%'
		//0 : # of examples
		//1 : # of features + 1 for name column
		//2 : format of columns -> 9 1 1 1 1 ...
		//3 : names of columns (not used by SOM_MAP)
		//format : 0 is training data to lrn, 1 is training data to svm format, 2 is testing data
		switch (savFileFrmt) {
			case "denseLRNData" : {
				saveLRNData();
				mapData.setFlag(dataSavedIDX, true);	
				break;
			}
			case "sparseSVMData" : {
				saveSVMData();
				mapData.setFlag(dataSavedIDX, true);		
				break;
			}
			case "denseCSVData" : {				
				saveCSVData();
				mapData.setFlag(dataSavedIDX, true);
				break;
			}
			default :{//default to save data in lrn format
				saveLRNData();
				mapData.setFlag(dataSavedIDX, true);				
			}
		}
		return true;
	}//call
}//straffDataWriter


