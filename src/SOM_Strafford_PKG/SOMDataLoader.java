package SOM_Strafford_PKG;

import java.io.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

import processing.core.PImage;

//class that describes the hierarchy of files required for running and analysing a SOM
public class SOMDataLoader implements Runnable {
	public SOMMapManager mapMgr;				//the map these files will use
	public SOMProjConfigData projConfigData;			//struct maintaining configuration information for entire project
	
	public final static float nodeDistThresh = 100000.0f;
	
	public int ftrTypeUsedToTrain;
	public boolean useChiSqDist;
	
	//public boolean loadFtrBMUs;

	private int[] stFlags;						//state flags - bits in array holding relevant process info
	public static final int
			debugIDX 				= 0,
			loadFtrBMUsIDX			= 1;
	public static final int numFlags = 2;
	//type of data used to train - 0 : unmodded, 1:std'ized, 2:normalized - should be retrieved from file name
	//private int dataFormat;
	
	public SOMDataLoader(SOMMapManager _mapMgr, boolean _lBMUs, SOMProjConfigData _configData) {
		mapMgr = _mapMgr;
		projConfigData = _configData;
		initFlags();
		setFlag(loadFtrBMUsIDX,  _lBMUs);
	}

	@Override
	public void run(){
		if(projConfigData.allReqFilesLoaded()){
			boolean success = execDataLoad() ;
			mapMgr.setFlag(SOMMapManager.mapDataLoadedIDX,success);
			mapMgr.setFlag(SOMMapManager.loaderRtnIDX,true);
			mapMgr.setMapImgClrs();
			mapMgr.dispMessage("DataLoader","run","Finished data loader : SOM Data Loaded : " + mapMgr.getFlag(SOMMapManager.mapDataLoadedIDX) + " | loader ret code : " +mapMgr.getFlag(SOMMapManager.loaderRtnIDX) );			
		}
		else {
			mapMgr.setFlag(SOMMapManager.loaderRtnIDX,false);
			mapMgr.dispMessage("DataLoader","run","Data loader Failed : fnames structure out of sync or file IO error ");
		}
	}
	//load results from map processing - fnames needs to be modified to handle this
	private boolean execDataLoad(){
		ftrTypeUsedToTrain = mapMgr.getCurrMapDataFrmt();
		useChiSqDist = mapMgr.getUseChiSqDist();
		//must load jp's and jpg's that were used for this map
		//load map weights for all map nodes
		boolean success = loadSOMWts();			
		//get training and testing data partitions that were used to train the map - TODO
		mapMgr.assignTrainTestData();
		//load mins and diffs of data used to train map
		success = loadDiffsMins();		
		//load SOM's best matching units for training data - must be after map wts and training data has been loaded
		success = loadSOM_BMUs();
		//if bmus loaded, set bmus for all products
		if (success) {
			mapMgr.setProductBMUs();
		} else {
			mapMgr.dispMessage("SOMDataLoader", "execDataLoad", "Unable to match products to map nodes since BMU loading failed");
		}
		//		//load SOM's sorted best matching units for each feature - must be after map wts and training data has been loaded
		if (getFlag(loadFtrBMUsIDX)){
			success = loadSOM_ftrBMUs();	
		}

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
		String diffsFileName = projConfigData.getDiffsFName(), minsFileName = projConfigData.getMinsFName();
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
		String [] strs= mapMgr.loadFileIntoStringAra(fileName, "Loaded data file : "+fileName, "Error reading file : "+fileName);
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
		if((tmapY != mapMgr.getMapY()) || (tmapX != mapMgr.getMapX())) { 
			mapMgr.dispMessage("DataLoader","checkMapDim","!!"+ errorMSG + " dimensions : " + tmapX +","+tmapY+" do not match dimensions of learned weights " + mapMgr.getMapX() +","+mapMgr.getMapY()+". Loading aborted."); 
			return false;} 
		return true;		
	}
	
	//load map wts from file built by SOM_MAP - need to know format of original data used to train map	
	//Map nodes are similar in format to training examples but scaled based on -their own- data
	//consider actual map data to be feature data, scale map nodes based on min/max feature data seen in wts file
	private boolean loadSOMWts(){//builds mapnodes structure - each map node's weights 
		String wtsFileName = projConfigData.getSOMResFName(projConfigData.wtsIDX);
		mapMgr.MapNodes = new TreeMap<Tuple<Integer,Integer>, SOMMapNodeExample>();
		if(wtsFileName.length() < 1){return false;}
		String [] strs= mapMgr.loadFileIntoStringAra(wtsFileName, "Loaded wts data file : "+wtsFileName, "Error wts reading file : "+wtsFileName);
		if(strs==null){return false;}
		String[] tkns,ftrNames;
		SOMMapNodeExample dpt;	
		int numEx = 0, mapX=1, mapY=1,numWtData = 0;
		Tuple<Integer,Integer> mapLoc;
		float[] tmpMapMaxs = null;
		for (int i=0;i<strs.length;++i){//load in data 
			if(i < 2){
				tkns = strs[i].replace('%', ' ').trim().split(mapMgr.SOM_FileToken);
				//set map size in nodes
				if(i==0){mapY = Integer.parseInt(tkns[0]);mapMgr.setMapY(mapY);mapX = Integer.parseInt(tkns[1]);mapMgr.setMapX(mapX);	} 
				else {	
					mapMgr.numFtrs = Integer.parseInt(tkns[0]);
					ftrNames = new String[mapMgr.numFtrs];
					for(int j=0;j<mapMgr.numFtrs;++j){ftrNames[j]=""+j;}					
					mapMgr.dataHdr = new dataDesc(mapMgr, ftrNames);				//assign numbers to feature name data header
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
			dpt = new SOMMapNodeExample(mapMgr, mapLoc, tkns);//give each map node its features
		
			++numEx;
			float[] ftrData = dpt.getFtrs();
			for(int d = 0; d<mapMgr.numFtrs; ++d){
				mapMgr.map_ftrsMean[d] += ftrData[d];
				tmpMapMaxs[d] = (tmpMapMaxs[d] < ftrData[d] ? ftrData[d]  : tmpMapMaxs[d]);
				mapMgr.map_ftrsMin[d] = (mapMgr.map_ftrsMin[d] > ftrData[d] ? ftrData[d]  : mapMgr.map_ftrsMin[d]);
			}
			mapMgr.MapNodes.put(mapLoc, dpt);			
			mapMgr.nodesWithNoEx.add(dpt);				//initialize : add all nodes to set, will remove nodes when they get mappings
		}
		//make sure both unmoddified features and std'ized features are built before determining map mean/var
		//need to have all features built to scale features		
		mapMgr.map_ftrsDiffs = new float[mapMgr.numFtrs];
		//initialize array of images to display map of particular feature with
		mapMgr.initMapAras(mapMgr.numFtrs);
		
		for(int d = 0; d<mapMgr.numFtrs; ++d){mapMgr.map_ftrsMean[d] /= 1.0f*numEx;mapMgr.map_ftrsDiffs[d]=tmpMapMaxs[d]-mapMgr.map_ftrsMin[d];}
		//set stdftrs for map nodes and variance calc
		float diff;
		//reset this to manage all map nodes
		mapMgr.initPerJPMapOfNodes();
		//for every node, now build standardized features 
		for(Tuple<Integer, Integer> key : mapMgr.MapNodes.keySet()){
			SOMMapNodeExample tmp = mapMgr.MapNodes.get(key);
			tmp.buildStdFtrsMapFromFtrData_MapNode(mapMgr.map_ftrsMin, mapMgr.map_ftrsDiffs);
			//accumulate map ftr moments
			float[] ftrData = tmp.getFtrs();
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
	
	//load best matching units for each training example - has values : idx, mapy, mapx.  Uses file built by som code.  can be verified by comparing actual example distance from each node
	private boolean loadSOM_BMUs(){//modifies existing nodes and datapoints only
		String bmFileName = projConfigData.getSOMResFName(projConfigData.bmuIDX);
		if(bmFileName.length() < 1){return false;}
		mapMgr.nodesWithEx.clear();
		String [] strs= mapMgr.loadFileIntoStringAra(bmFileName, "Loaded best matching unit data file : "+bmFileName, "Error reading best matching unit file : "+bmFileName);
		if(strs==null){return false;}
		String[] tkns;
		if (useChiSqDist) {		//setting chi check here to minimize if checks.  hundreds of thousands (if not millions) of if checks per load.	 May need to stream load
			for (int i=0;i<strs.length;++i){//load in data on all bmu's
				if(i < 2){
					tkns = strs[i].replace('%', ' ').trim().split(mapMgr.SOM_FileToken);
					if(i==0){
						if(!checkMapDim(tkns,"Best Matching Units file " + getFName(bmFileName))){return false;}//otw continue
					} else {	
						int tNumTDat = Integer.parseInt(tkns[0]);
						if(tNumTDat != mapMgr.numTrainData) { //don't forget added emtpy vector
							mapMgr.dispMessage("DataLoader","loadSOM_BMUs","!!Best Matching Units file " + getFName(bmFileName) + " # of training examples : " + tNumTDat +" does not match # of training examples in training data " + mapMgr.numTrainData+". Loading aborted." ); 
							return false;}
					}
					continue;
				} 
				tkns = strs[i].split(mapMgr.SOM_FileToken);
				if(tkns.length < 2){continue;}//shouldn't happen				
				Tuple<Integer,Integer> mapLoc = new Tuple<Integer, Integer>(Integer.parseInt(tkns[2]),Integer.parseInt(tkns[1]));//map locations in bmu data are in (y,x) order (row major)
				SOMMapNodeExample tmpMapNode = mapMgr.MapNodes.get(mapLoc);
				if(null==tmpMapNode){ mapMgr.dispMessage("DataLoader","loadSOM_BMUs","!!!!!!!!!Map node stated as best matching unit for training example " + tkns[0] + " not found in map ... somehow. "); return false;}//catastrophic error shouldn't be possible
				Integer dpIdx = Integer.parseInt(tkns[0]);	//datapoint index in training data		
				StraffSOMExample tmpDataPt = mapMgr.trainData[dpIdx];
				if(null==tmpDataPt){ mapMgr.dispMessage("DataLoader","loadSOM_BMUs","!!Training Datapoint given by idx in BMU file str tok : " + tkns[0] + " of string : --" + strs[i] + "-- not found in training data. "); return false;}//catastrophic error shouldn't happen
				//must have mapMgr.map_ftrsVar known by here for chi-sq dist 
				tmpDataPt.setBMU_ChiSq(tmpMapNode, ftrTypeUsedToTrain);
				//debug to verify node row/col order
				//dbgVerifyBMUs(tmpMapNode, tmpDataPt,Integer.parseInt(tkns[1]) ,Integer.parseInt(tkns[2]));
				mapMgr.nodesWithEx.add(tmpMapNode);
				mapMgr.nodesWithNoEx.remove(tmpMapNode);
				//mapMgr.dispMessage("DataLoader : Tuple "  + mapLoc + " from str @ i-2 = " + (i-2) + " node : " + tmpMapNode.toString());
			}//for each training data point			
		} else {			
			for (int i=0;i<strs.length;++i){//load in data on all bmu's
				if(i < 2){
					tkns = strs[i].replace('%', ' ').trim().split(mapMgr.SOM_FileToken);
					if(i==0){
						if(!checkMapDim(tkns,"Best Matching Units file " + getFName(bmFileName))){return false;}//otw continue
					} else {	
						int tNumTDat = Integer.parseInt(tkns[0]);
						if(tNumTDat != mapMgr.numTrainData) { //don't forget added emtpy vector
							mapMgr.dispMessage("DataLoader","loadSOM_BMUs","!!Best Matching Units file " + getFName(bmFileName) + " # of training examples : " + tNumTDat +" does not match # of training examples in training data " + mapMgr.numTrainData+". Loading aborted." ); 
							return false;}
					}
					continue;
				} 
				tkns = strs[i].split(mapMgr.SOM_FileToken);
				if(tkns.length < 2){continue;}//shouldn't happen
				
				Tuple<Integer,Integer> mapLoc = new Tuple<Integer, Integer>(Integer.parseInt(tkns[2]),Integer.parseInt(tkns[1]));//map locations in bmu data are in (y,x) order (row major)
				SOMMapNodeExample tmpMapNode = mapMgr.MapNodes.get(mapLoc);
				if(null==tmpMapNode){ mapMgr.dispMessage("DataLoader","loadSOM_BMUs","!!!!!!!!!Map node stated as best matching unit for training example " + tkns[0] + " not found in map ... somehow. "); return false;}//catastrophic error shouldn't be possible
				Integer dpIdx = Integer.parseInt(tkns[0]);	//datapoint index in training data		

				StraffSOMExample tmpDataPt = mapMgr.trainData[dpIdx];
				if(null==tmpDataPt){ mapMgr.dispMessage("DataLoader","loadSOM_BMUs","!!Training Datapoint given by idx in BMU file str tok : " + tkns[0] + " of string : --" + strs[i] + "-- not found in training data. "); return false;}//catastrophic error shouldn't happen
				//passing per-ftr variance for chi sq distan			
				//using variance for chi dq dist calculation - also sets distance of node from bmu using whatever the current distance calculation is set to be
				//must have mapMgr.map_ftrsVar known by here for chi-sq dist 
				tmpDataPt.setBMU(tmpMapNode, ftrTypeUsedToTrain);
				//debug to verify node row/col order
				//dbgVerifyBMUs(tmpMapNode, tmpDataPt,Integer.parseInt(tkns[1]) ,Integer.parseInt(tkns[2]));
				mapMgr.nodesWithEx.add(tmpMapNode);
				mapMgr.nodesWithNoEx.remove(tmpMapNode);
				//mapMgr.dispMessage("DataLoader : Tuple "  + mapLoc + " from str @ i-2 = " + (i-2) + " node : " + tmpMapNode.toString());
			}//for each training data point	
		}//if chisq dist else
		boolean isTorroid = mapMgr.isToroidal();
		float dist,minDist;
		//set all empty mapnodes to have a label based on the closest mapped node's label
		if (isTorroid) {//minimize in-loop if checks
			for(SOMMapNodeExample node : mapMgr.nodesWithNoEx){//node has no label mappings, so need to determine label
				minDist = 1000000;
				SOMMapNodeExample closest  = node;					//will never be added
				for(SOMMapNodeExample node2 : mapMgr.nodesWithEx){		//this is adding a -map- node
					dist = getSqMapDist_torr(node2, node);			//actual map topology dist - need to handle wrapping!
					if (dist < minDist) {minDist = dist;		closest = node2;}
					//node.addBMUExample(dist, node2);			//adds a -map- node we know has a label - slow to add all of them, and unnecessary - only need top node
				}	
				node.addBMUExample(minDist, closest);			//adds single closest -map- node we know has a label, or itself if none found
			}
		} else {
			for(SOMMapNodeExample node : mapMgr.nodesWithNoEx){//node has no label mappings, so need to determine label
				minDist = 1000000;
				SOMMapNodeExample closest  = node;					//will never be added
				for(SOMMapNodeExample node2 : mapMgr.nodesWithEx){		//this is adding a -map- node
					dist = getSqMapDist_flat(node2, node);			//actual map topology dist - need to handle wrapping!
					if (dist < minDist) {minDist = dist;		closest = node2;}
					//node.addBMUExample(dist, node2);			//adds a -map- node we know has a label - slow to add all of them, and unnecessary - only need top node
				}	
				node.addBMUExample(minDist, closest);			//adds single closest -map- node we know has a label, or itself if none found
			}			
		}
		
		mapMgr.dispMessage("DataLoader","loadSOM_BMUs","Finished Loading SOM BMUs from file : " + getFName(bmFileName) + "| Found "+mapMgr.nodesWithEx.size()+" nodes with example mappings.");
		return true;
	}//loadSOM_BMs
	
	//verify that map node coords are in proper order (row-col vs x-y)
	private void dbgVerifyBMUs(SOMMapNodeExample tmpMapNode, StraffSOMExample tmpDataPt, Integer x, Integer y) {
		//this is alternate node with column-major key
		Tuple<Integer,Integer> mapAltLoc = new Tuple<Integer, Integer>(x,y);//verifying correct row/col order - tmpMapNode should be closer to mapMgr.trainData[dpIdx] than to tmpAltMapNode
		SOMMapNodeExample tmpAltMapNode = mapMgr.MapNodes.get(mapAltLoc);
		//if using chi-sq dist, must know mapMgr.map_ftrsVar by now
		//double tmpDist =  mapMgr.dpDistFunc(tmpAltMapNode, tmpDataPt);
		double tmpDist;
		if (useChiSqDist) {
			tmpDist =  tmpDataPt.getSqDistFromFtrType_ChiSq(tmpAltMapNode, ftrTypeUsedToTrain);  //mapMgr.dpDistFunc(tmpAltMapNode, tmpDataPt);
		} else {
			tmpDist =  tmpDataPt.getSqDistFromFtrType(tmpAltMapNode,ftrTypeUsedToTrain);  //mapMgr.dpDistFunc(tmpAltMapNode, tmpDataPt);
		}
		if(tmpDist < tmpDataPt._distToBMU ) {
			mapMgr.dispMessage("DataLoader","loadSOM_BMUs:dbgVerifyBMUs","Somehow bmu calc is incorrect - x/y order of map node location perhaps is swapped? dataPt " + tmpDataPt.OID + " is closer to "+ tmpAltMapNode.OID + " than to predicted BMU : " + tmpMapNode.OID+" : dists : " +tmpDist + " vs. " +tmpDataPt._distToBMU);
		}
	}//dbgVerifyBMUs	
	
	//returns sq distance between two map locations - needs to handle wrapping if map built torroidally
	public float getSqMapDist_flat(SOMMapNodeExample a, SOMMapNodeExample b){		return (a.mapLoc._SqrDist(b.mapLoc));	}//	
	//returns sq distance between two map locations - needs to handle wrapping if map built torroidally
	public float getSqMapDist_torr(SOMMapNodeExample a, SOMMapNodeExample b){
		float 
			oldXa = a.mapLoc.x - b.mapLoc.x, oldXaSq = oldXa*oldXa,
			newXa = oldXa + mapMgr.getMapWidth(), newXaSq = newXa*newXa,
			oldYa = a.mapLoc.y - b.mapLoc.y, oldYaSq = oldYa*oldYa,
			newYa = oldYa + mapMgr.getMapHeight(), newYaSq = newYa*newYa;
		return (oldXaSq < newXaSq ? oldXaSq : newXaSq ) + (oldYaSq < newYaSq ? oldYaSq : newYaSq);
	}//
	
	//load the units that have the best performance per feature for each feature.  This can be safely ignored
	//this is built off a file that is generated from SOM code (extension .fwts); the code to build this file is not part of vanilla som code, but was added
	private boolean loadSOM_ftrBMUs(){
		String ftrBMUFname =  projConfigData.getSOMResFName(projConfigData.fwtsIDX);
		if(ftrBMUFname.length() < 1){return false;}
		String [] strs= mapMgr.loadFileIntoStringAra(ftrBMUFname, "Loaded features with bmu data file : "+ftrBMUFname, "Error reading feature bmu file : "+ftrBMUFname);
		if((strs==null) || (strs.length == 0)){
			mapMgr.dispMessage("DataLoader","loadSOM_ftrBMUs","Ftr-based BMU File not found.  The code generating this file is not a part of vanilla SOM, but rather was added to the SOM code by John. This error (and the missing data) can be safely ignored.");			
			return false;}
		String[] tkns;
		SOMFeature tmp;
		ArrayList<SOMFeature> tmpAra = new ArrayList<SOMFeature>();
		for (int i=0;i<strs.length;++i){//load in data 
			if(i < 2){
				tkns = strs[i].replace('%', ' ').trim().split(mapMgr.SOM_FileToken);
				if(i==0){
					if(!checkMapDim(tkns,"Feature Best Matching Units file " + getFName(ftrBMUFname))){return false;}
				} else {	
					int tNumTFtr = Integer.parseInt(tkns[0]);
					if(tNumTFtr != mapMgr.numFtrs) { 
						mapMgr.dispMessage("DataLoader","loadSOM_ftrBMUs","!!Best Matching Units file " + getFName(ftrBMUFname) + " # of training examples : " + tNumTFtr +" does not match # of training examples in training data " + mapMgr.numFtrs+". Loading aborted." ); 
						return false;}
				}
				continue;
			} 
			tkns = strs[i].split(":");
			tmp = new SOMFeature(mapMgr,tkns[0].trim(),Integer.parseInt(tkns[0].trim()),tkns[1].trim().split(mapMgr.SOM_FileToken));
			tmpAra.add(tmp);
		}
		mapMgr.featuresBMUs = tmpAra.toArray(new SOMFeature[0]);		
		mapMgr.dispMessage("DataLoader","loadSOM_ftrBMUs","Finished Loading SOM per-feature BMU list from file : " + getFName(ftrBMUFname));
		return true;
	}//loadSOM_ftrBMUs	
	
	private void initFlags(){stFlags = new int[1 + numFlags/32]; for(int i = 0; i<numFlags; ++i){setFlag(i,false);}}
	public void setFlag(int idx, boolean val){
		int flIDX = idx/32, mask = 1<<(idx%32);
		stFlags[flIDX] = (val ?  stFlags[flIDX] | mask : stFlags[flIDX] & ~mask);
		switch (idx) {//special actions for each flag
			case debugIDX : {break;}	
			case loadFtrBMUsIDX : {break;}
		}
	}//setFlag	
	public boolean getFlag(int idx){int bitLoc = 1<<(idx%32);return (stFlags[idx/32] & bitLoc) == bitLoc;}		
}//dataLoader


//class to determine which products are closest to which map nodes
//partition list of product examples to find bmus
class straffProductsToMapBuilder implements Callable<Boolean>{
	SOMMapManager mapMgr;
	int stIdx, endIdx, curMapFtrType, thdIDX;
	boolean useChiSqDist;
	public straffProductsToMapBuilder(SOMMapManager _mapMgr, int _stProdIDX, int _endProdIDX, boolean _useChiSqDist, int _thdIDX) {
		mapMgr = _mapMgr;
		stIdx = _stProdIDX;
		endIdx = _endProdIDX;
		useChiSqDist =_useChiSqDist;
		thdIDX= _thdIDX;
		curMapFtrType = mapMgr.getCurrMapDataFrmt();
	}	
	@Override
	public Boolean call() throws Exception {
		//for every product find closest map node
		mapMgr.dispMessage("straffProductsToMapBuilder", "Run Thread : " +thdIDX, "Starting product mapping");
		if (useChiSqDist) {
			for (int i=stIdx;i<endIdx;++i) {	mapMgr.productData[i].findBMUFromNodes_ChiSq(mapMgr.MapNodes, curMapFtrType);	}					
		} else {
			for (int i=stIdx;i<endIdx;++i) {	mapMgr.productData[i].findBMUFromNodes(mapMgr.MapNodes,  curMapFtrType);	}				
		}
		mapMgr.dispMessage("straffProductsToMapBuilder", "Run Thread : " +thdIDX, "Finished product mapping");
		return true;
	}	
}//straffProductsToMapBuilder

//this will build a single image of the map based on ftr data
class straffMapVisImgBuilder implements Callable<Boolean>{
	SOMMapManager mapMgr;
	int mapX, mapY;
	int xSt, xEnd, ySt, yEnd;
	int imgW;
	//type of features to use to build vis, based on type used to train map (unmodified, stdftrs, normftrs)
	int ftrType;
	float mapScaleVal, sclMultXPerPxl, sclMultYPerPxl;
	TreeMap<Tuple<Integer,Integer>, SOMMapNodeExample> MapNodes;
	private PImage[] mapLocClrImg;
	public straffMapVisImgBuilder(SOMMapManager _mapMgr, PImage[] _mapLocClrImg, int[] _xVals, int[] _yVals,  float _mapScaleVal) {
		mapMgr = _mapMgr;
		MapNodes = mapMgr.MapNodes;
		ftrType = mapMgr.getCurrMapDataFrmt();
		mapLocClrImg = _mapLocClrImg;
		mapX = mapMgr.mapX;
		xSt = _xVals[0];
		xEnd = _xVals[1];
		if(mapLocClrImg.length == 0) {		imgW = 0;} 
		else {								imgW = mapLocClrImg[0].width;}
		mapY = mapMgr.mapY;
		ySt = _yVals[0];
		yEnd = _yVals[1];

		mapScaleVal = _mapScaleVal;
		sclMultXPerPxl = mapScaleVal * mapMgr.nodeXPerPxl;
		sclMultYPerPxl = mapScaleVal * mapMgr.nodeYPerPxl;
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
		//int xInt = (int) Math.floor(x), yInt = (int) Math.floor(y), xIntp1 = (xInt+1)%SOM_Data.mapX, yIntp1 = (yInt+1)%SOM_Data.mapY;		//assume torroidal map		
		//need to divide by width/height of map * # cols/rows to get mapped to actual map nodes
		//dispMessage("In getDataPointAtLoc : Mouse loc in Nodes : " + x + ","+y+ "\txInt : "+ xInt + " yInt : " + yInt );
		float xInterp = (x+1) %1, yInterp = (y+1) %1;
		//always compare standardized feature data in test/train data to standardized feature data in map
		TreeMap<Integer, Float> LowXLowYFtrs = MapNodes.get(new Tuple<Integer, Integer>(xInt,yInt)).getCurrentFtrMap(ftrType), LowXHiYFtrs= MapNodes.get(new Tuple<Integer, Integer>(xInt,yIntp1)).getCurrentFtrMap(ftrType),
				 HiXLowYFtrs= MapNodes.get(new Tuple<Integer, Integer>(xIntp1,yInt)).getCurrentFtrMap(ftrType),  HiXHiYFtrs= MapNodes.get(new Tuple<Integer, Integer>(xIntp1,yIntp1)).getCurrentFtrMap(ftrType);
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
			ProspectExample oldEx = mapMgr.prospectMap.put(ex.OID, ex);	
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
	private StraffSOMExample[] exAra;
	private int numFtrs,numSmpls;
	private String savFileFrmt;
	private String fileName;
	
	public straffDataWriter(SOMMapManager _mapData, int _dataFrmt, int _dataSavedIDX, String _fileName, String _savFileFrmt, StraffSOMExample[] _exAra) {
		mapData = _mapData;
		dataFrmt = _dataFrmt;		//either unmodified, standardized or normalized -> 0,1,2
		exAra = _exAra;
		numFtrs = mapData.numFtrs;
		numSmpls = exAra.length;
		savFileFrmt = _savFileFrmt;
		fileName = _fileName;
		dataSavedIDX = _dataSavedIDX;
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
		mapData.saveStrings(fileName,outStrings);		
		mapData.dispMessage("straffDataWriter","saveLRNData","Finished saving .lrn file with " + outStrings.length+ " elements to file : "+ fileName);			
	}//save lrn train data
	
	//save data in csv format
	private void saveCSVData() {
		//use buildInitLRN for test and train
		String[] outStrings = buildInitLRN();
		int strIDX = 4;
		for (int i=0;i<exAra.length; ++i) {outStrings[i+strIDX]=exAra[i].toCSVString(dataFrmt);	}
		mapData.saveStrings(fileName,outStrings);		
		mapData.dispMessage("straffDataWriter","saveCSVData","Finished saving .csv file with " + outStrings.length+ " elements to file : "+ fileName);			
	}//save csv test data
	
	//save data in SVM record form - each record is like a map/dict -> idx: val pair.  designed for sparse data
	private void saveSVMData() {
		//need to save a vector to determine the 
		String[] outStrings = new String[numSmpls];
		for (int i=0;i<exAra.length; ++i) {outStrings[i]=exAra[i].toSVMString(dataFrmt);	}
//		String[] outStrings = new String[numSmpls+1];
//		for (int i=0;i<exAra.length; ++i) {outStrings[i]=exAra[i].toSVMString(dataFrmt);	}
//		//need dummy training record to manage all features, incase some are 0 in all training examples - they won't be present in svm sparse format
//		String res="";
//		for(int i=0;i<numFtrs;++i) {res += "" + i  + ":" + String.format("%1.7g", 0.0f) + " ";}
//		outStrings[exAra.length] = res;
		mapData.saveStrings(fileName,outStrings);		
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


