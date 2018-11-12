package SOM_Strafford_PKG;

import java.io.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;

//class that describes the hierarchy of files required for running and analysing a SOM
public class SOMDataLoader implements Runnable {
	public SOMMapData map;				//the map these files will use
	public SOMDatFileConfig fnames;			//struct maintaining file names for all files in som, along with 
	
	public final static float nodeDistThresh = 100000.0f;
	//idxs of different kinds of files
	public static final int
		wtsIDX = 0,
		fwtsIDX = 1,
		bmuIDX = 2,
		umtxIDX = 3;	
	
	//public boolean loadFtrBMUs;

	private int[] stFlags;						//state flags - bits in array holding relevant process info
	public static final int
			debugIDX 				= 0,
			loadFtrBMUsIDX			= 1;
	public static final int numFlags = 2;
	
	public SOMDataLoader(SOMMapData _map, boolean _lBMUs, SOMDatFileConfig _fnames) {
		map = _map;
		fnames = _fnames;
		initFlags();
		setFlag(loadFtrBMUsIDX,  _lBMUs);
	}

	@Override
	public void run(){
		if(fnames.allReqFilesLoaded()){
			boolean success =  execDataLoad() ;
			map.setFlag(SOMMapData.dataLoadedIDX,success);
			map.setFlag(SOMMapData.loaderRtnIDX,true);
			map.setMapImgClrs();
			map.dispMessage("DataLoader : Finished data loader : data Loaded : " + map.getFlag(SOMMapData.dataLoadedIDX) + " | loader ret code : " +map.getFlag(SOMMapData.loaderRtnIDX) );			
		}
		else {
			map.setFlag(SOMMapData.loaderRtnIDX,false);
			map.dispMessage("DataLoader : Data loader Failed : fnames structure out of sync or file IO error ");
		}
	}
	//load results from map processing - fnames needs to be modified to handle this
	private boolean execDataLoad(){
		//must load jp's and jpg's that were used for this map
		//load map weights for all map nodes
		boolean success = loadSOMWts();			
		//get training and testing data partitions that were used to train the map
		map.assignTrainTestData();
		//set all features and scaled features for all loaded dataPoints and SOMmapNodes - requires that all mins and diffs be loaded
		success = condAllData();		
		//load SOM's best matching units for training data - must be after map wts and training data has been loaded
		success = loadSOM_BMUs();
//		//load SOM's sorted best matching units for each feature - must be after map wts and training data has been loaded
		if (getFlag(loadFtrBMUsIDX)){
			success = loadSOM_ftrBMUs();	
		}
		//save csv class files
		//map.setSaveLocClrImg(true);
		return success;
	}//execDataLoad
	
	//return file name from file name and path name
	protected String getFName(String fNameAndPath){
		File file = new File(fNameAndPath);
		String simpleFileName = file.getName();
		return simpleFileName;
	}
	
	//this will make sure that all scaled features are actually scaled and nonscaled are actually nonscaled
	public boolean condAllData(){
		String diffsFileName = fnames.getDiffsFname(), minsFileName = fnames.getMinsFname();
		//load normalizing values for datapoints in weights - differences and mins, used to scale/descale training and map data
		map.diffsVals = loadCSVSrcDataPoint(diffsFileName);
		if((null==map.diffsVals) || (map.diffsVals.length < 1)){map.dispMessage("DataLoader : !!error reading diffsFile : " + diffsFileName); return false;}
		map.minsVals = loadCSVSrcDataPoint(minsFileName);
		if((null==map.minsVals)|| (map.minsVals.length < 1)){map.dispMessage("DataLoader : !!error reading minsFile : " + minsFileName); return false;}	

		//TODO set standardized node values here?
		
		//for map nodes - descale features aras for map nodes
//		for(Tuple<Integer,Integer> key : map.MapNodes.keySet()){SOMMapNodeExample tmp = map.MapNodes.get(key); }//tmp.setCorrectScaling();}//p.pr(tmp.toString());}
		//training & testing data points scale/descale
//		for(int i=0; i<map.trainData.length;++i){map.trainData[i].setCorrectScaling(); }//p.pr(trainData[i].toString());}
//		for(int i=0; i<map.testData.length;++i){map.testData[i].setCorrectScaling();}
		return true;
	}//condAllData()
	
	//read file with scaling/min values for Map to convert data back to original feature space - single row of data
	private Float[] loadCSVSrcDataPoint(String fileName){		
		if(fileName.length() < 1){return null;}
		String [] strs= map.loadFileIntoStringAra(fileName, "Loaded data file : "+fileName, "Error reading file : "+fileName);
		if(strs==null){return null;}
		//strs should only be length 1
		if(strs.length > 1){map.dispMessage("DataLoader : error reading file : " + fileName + " String array has more than 1 row.");return null;}		
		String[] tkns = strs[0].split(map.csvFileToken);
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
		if((tmapY != map.getMapY()) || (tmapX != map.getMapX())) { 
			map.dispMessage("DataLoader : !!"+ errorMSG + " dimensions : " + tmapX +","+tmapY+" do not match dimensions of learned weights " + map.getMapX() +","+map.getMapY()+". Loading aborted."); 
			return false;} 
		return true;		
	}
	
	//load map wts from file built by SOM_MAP - need to know format of original data used to train map
	
	private boolean loadSOMWts(){//builds mapnodes structure - each map node's weights 
		String wtsFileName = fnames.getSOMResFName(wtsIDX);
		map.MapNodes = new TreeMap<Tuple<Integer,Integer>, SOMMapNodeExample>();
		if(wtsFileName.length() < 1){return false;}
		String [] strs= map.loadFileIntoStringAra(wtsFileName, "Loaded wts data file : "+wtsFileName, "Error wts reading file : "+wtsFileName);
		if(strs==null){return false;}
		String[] tkns,ftrNames;
		SOMMapNodeExample dpt;	
		int numEx = 0, mapX=1, mapY=1,numWtData = 0;
		Tuple<Integer,Integer> mapLoc;
		float[] tmpMapMaxs = null;
		for (int i=0;i<strs.length;++i){//load in data 
			if(i < 2){
				tkns = strs[i].replace('%', ' ').trim().split(map.SOM_FileToken);
				//set map size in nodes
				if(i==0){mapY = Integer.parseInt(tkns[0]);map.setMapY(mapY);mapX = Integer.parseInt(tkns[1]);map.setMapX(mapX);	} 
				else {	
					map.numFtrs = Integer.parseInt(tkns[0]);
					ftrNames = new String[map.numFtrs];
					for(int j=0;j<map.numFtrs;++j){ftrNames[j]=""+j;}					
					map.dataHdr = new dataDesc(map, ftrNames);				//assign numbers to feature name data header
					map.map_ftrsMean = new float[map.numFtrs];
					tmpMapMaxs = new float[map.numFtrs];
					map.map_ftrsMin = new float[map.numFtrs];
					for(int l=0;l<map.numFtrs;++l) {map.map_ftrsMin[l]=10000.0f;}//need to init to big number to get accurate min
					map.map_ftrsVar = new float[map.numFtrs];
				}	
				continue;
			}//if first 2 lines in wts file
			tkns = strs[i].split(map.SOM_FileToken);
			if(tkns.length < 2){continue;}
			mapLoc = new Tuple<Integer, Integer>((i-2)%mapX, (i-2)/mapX);//map locations in som data are increasing in x first, then y (row major)
			dpt = new SOMMapNodeExample(map, mapLoc, tkns);//give each map node its color, so that if the map is displayed in color, the node and its text can be the opposite color and contrast
			++numEx;
			float[] ftrData = dpt.getFtrs();
//			dbgDispFtrAra(tmp.getStdFtrs(), "raw ftrs");
			for(int d = 0; d<map.numFtrs; ++d){
				map.map_ftrsMean[d] += ftrData[d];
				tmpMapMaxs[d] = (tmpMapMaxs[d] < ftrData[d] ? ftrData[d]  : tmpMapMaxs[d]);
				map.map_ftrsMin[d] = (map.map_ftrsMin[d] > ftrData[d] ? ftrData[d]  : map.map_ftrsMin[d]);
			}
			map.MapNodes.put(mapLoc, dpt);			
			map.nodesWithNoEx.add(dpt);				//add all nodes to set, will remove nodes when they get mappings
		}
		map.map_ftrsDiffs = new float[map.numFtrs];
		//build map array of images to map to
		map.initMapAras(map.numFtrs);
		
		for(int d = 0; d<map.numFtrs; ++d){map.map_ftrsMean[d] /= 1.0f*numEx;map.map_ftrsDiffs[d]=tmpMapMaxs[d]-map.map_ftrsMin[d];}
		//set stdftrs for map nodes and variance calc
		float diff;
		for(Tuple<Integer, Integer> key : map.MapNodes.keySet()){
			SOMMapNodeExample tmp = map.MapNodes.get(key);
			float[] ftrData = tmp.getFtrs();
			for(int d = 0; d<map.numFtrs; ++d){
				diff = map.map_ftrsMean[d] - ftrData[d];
				map.map_ftrsVar[d] += diff*diff;
			}
			tmp.buildStdFtrsMap_MapNode(map.map_ftrsMin, map.map_ftrsDiffs);
			//dbgDispFtrAra(tmp.getStdFtrs(), "Std ftrs");
		}
		for(int d = 0; d<map.numFtrs; ++d){map.map_ftrsVar[d] /= 1.0f*numEx;}
		map.dispMessage("DataLoader : Finished Loading SOM weight data from file : " + getFName(wtsFileName) );
		
		return true;
	}//loadSOMWts	
	private void dbgDispFtrAra(float[] ftrData, String exStr) {
		System.out.println(ftrData.length + " " +exStr + " vals : ");		
		System.out.print(""+String.format("%.4f",ftrData[0]));
		for(int d=1;d<ftrData.length;++d) {System.out.print(", " + String.format("%.4f", ftrData[d]));		}
		System.out.println("");
	}//dbgDispFtrAra
	
	//load best matching units for each training example - has values : idx, mapy, mapx
	private boolean loadSOM_BMUs(){//modifies existing nodes and datapoints only
		String bmFileName = fnames.getSOMResFName(bmuIDX);
		if(bmFileName.length() < 1){return false;}
		map.nodesWithEx.clear();
		String [] strs= map.loadFileIntoStringAra(bmFileName, "Loaded best matching unit data file : "+bmFileName, "Error reading best matching unit file : "+bmFileName);
		if(strs==null){return false;}
		String[] tkns;
		for (int i=0;i<strs.length;++i){//load in data 
			if(i < 2){
				tkns = strs[i].replace('%', ' ').trim().split(map.SOM_FileToken);
				if(i==0){
					if(!checkMapDim(tkns,"Best Matching Units file " + getFName(bmFileName))){return false;}
				} else {	
					int tNumTDat = Integer.parseInt(tkns[0]);
					if(tNumTDat != map.numTrainData) { 
						map.dispMessage("DataLoader : !!Best Matching Units file " + getFName(bmFileName) + " # of training examples : " + tNumTDat +" does not match # of training examples in training data " + map.numTrainData+". Loading aborted." ); 
						return false;}
				}
				continue;
			} 
			tkns = strs[i].split(map.SOM_FileToken);
			if(tkns.length < 2){continue;}
			Tuple<Integer,Integer> mapLoc = new Tuple<Integer, Integer>(Integer.parseInt(tkns[2]),Integer.parseInt(tkns[1]));//map locations in bmu data are in (y,x) order (row major)
			SOMMapNodeExample tmpMapNode = map.MapNodes.get(mapLoc);
			if(null==tmpMapNode){ map.dispMessage("DataLoader : !!Map node stated as best matching unit for training example " + tkns[0] + " not found in map ... somehow. "); return false;}//catastrophic error shouldn't happen
			Integer dpIdx = Integer.parseInt(tkns[0]);
			//dataPoint tmpDP = map.trainData[dpIdx];
			if(null==map.trainData[dpIdx]){ map.dispMessage("DataLoader : !!Training Datapoint given by idx in BMU file str tok : " + tkns[0] + " of string : --" + strs[i] + "-- not found in training data. "); return false;}//catastrophic error shouldn't happen
			//passing per-ftr variance for chi sq distance
			//tmpDP.setBMU(tmpMapNode, map.map_ftrsVar);
			map.trainData[dpIdx].setBMU(tmpMapNode, map.map_ftrsVar);
			map.nodesWithEx.add(tmpMapNode);
			map.nodesWithNoEx.remove(tmpMapNode);
			//map.dispMessage("DataLoader : Tuple "  + mapLoc + " from str @ i-2 = " + (i-2) + " node : " + tmpMapNode.toString());
		}
		//float nodeDistThresh = 
		//set all empty mapnodes to have a label based on the most common label of their 4 neighbors (up,down,left,right)
		for(SOMMapNodeExample node : map.nodesWithNoEx){
			//tmpMapNode has no mappings, so need to determine label
			for(SOMMapNodeExample node2 : map.nodesWithEx){
				float dist = getSqMapDist(node2, node);			//actual map topology dist - need to handle wrapping!
				//if(dist <= nodeDistThresh){					//pxl distance
					node.addBMUExample(dist, node2);			//adds a node we know has a label - ugh
				//}
			}			
		}
		
		map.dispMessage("DataLoader : Finished Loading SOM BMUs from file : " + getFName(bmFileName) + "| Found "+map.nodesWithEx.size()+" nodes with example mappings.");
		return true;
	}//loadSOM_BMs
	//returns sq distance between two map locations - needs to handle wrapping if map built torroidally
	public float getSqMapDist(SOMMapNodeExample a, SOMMapNodeExample b){
		float aDist = (a.mapLoc._SqrDist(b.mapLoc));
		if (map.isToroidal()){//need to check distances
			float 
				oldXa = a.mapLoc.x - b.mapLoc.x, oldXaSq = oldXa*oldXa,
				newXa = oldXa + map.getMapWidth(), newXaSq = newXa*newXa,
				oldYa = a.mapLoc.y - b.mapLoc.y, oldYaSq = oldYa*oldYa,
				newYa = oldYa + map.getMapHeight(), newYaSq = newYa*newYa;
			return (oldXaSq < newXaSq ? oldXaSq : newXaSq ) + (oldYaSq < newYaSq ? oldYaSq : newYaSq);
		} else {return aDist;	}//not torroidal map, so direct distance is fine
	}
	
	private boolean loadSOM_ftrBMUs(){
		String ftrBMUFname =  fnames.getSOMResFName(fwtsIDX);
		if(ftrBMUFname.length() < 1){return false;}
		String [] strs= map.loadFileIntoStringAra(ftrBMUFname, "Loaded features with bmu data file : "+ftrBMUFname, "Error reading feature bmu file : "+ftrBMUFname);
		if(strs==null){return false;}
		String[] tkns;
		SOMFeature tmp;
		ArrayList<SOMFeature> tmpAra = new ArrayList<SOMFeature>();
		for (int i=0;i<strs.length;++i){//load in data 
			if(i < 2){
				tkns = strs[i].replace('%', ' ').trim().split(map.SOM_FileToken);
				if(i==0){
					if(!checkMapDim(tkns,"Feature Best Matching Units file " + getFName(ftrBMUFname))){return false;}
				} else {	
					int tNumTFtr = Integer.parseInt(tkns[0]);
					if(tNumTFtr != map.numFtrs) { 
						map.dispMessage("DataLoader : !!Best Matching Units file " + getFName(ftrBMUFname) + " # of training examples : " + tNumTFtr +" does not match # of training examples in training data " + map.numFtrs+". Loading aborted." ); 
						return false;}
				}
				continue;
			} 
			tkns = strs[i].split(":");
			tmp = new SOMFeature(map,tkns[0].trim(),Integer.parseInt(tkns[0].trim()),tkns[1].trim().split(map.SOM_FileToken));
			tmpAra.add(tmp);
		}
		map.featuresBMUs = tmpAra.toArray(new SOMFeature[0]);		
		map.dispMessage("DataLoader : Finished Loading SOM per-feature BMU list from file : " + getFName(ftrBMUFname));
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

//this class will load the pre-procced csv data into the prospect data structure owned by the SOMMapData object
//TODO this will speed up initial load of preprocced csv data.  Not a current priority
class straffCSVDataLoader implements Callable<Boolean>{
	public SOMMapData map;
	
	public straffCSVDataLoader(SOMMapData _map) {map=_map;}
	//load the partitioned file into the prospect data map
	private void loadData() {}

	@Override
	public Boolean call() throws Exception {	
		
		return true;
	}
	
}
//save all Strafford training/testing data to appropriate format for SOM
class straffDataWriter implements Runnable{
	//public SOM_StraffordMain pa;
	private SOMMapData map;	
	private int dataFrmt;
	private int dataSavedIDX;			//idx in flags array of SOMMapData object to denote what data was saved
	private StraffSOMExample[] exAra;
	private int numFtrs,numSmpls;
	private String savFileFrmt;
	private String fileName;
	
	public straffDataWriter(SOMMapData _map, int _dataFrmt, int _dataSavedIDX, String _fileName, String _savFileFrmt, StraffSOMExample[] _exAra) {
		map = _map;
		dataFrmt = _dataFrmt;		//either unmodified, standardized or normalized -> 0,1,2
		exAra = _exAra;
		numFtrs = map.numFtrs;
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
		map.saveStrings(fileName,outStrings);		
		map.dispMessage("Finished saving .lrn file with " + exAra.length+ " elements to file : "+ fileName);			
	}//save lrn train data
	
	//save data in csv format
	private void saveCSVData() {
		//use buildInitLRN for test and train
		String[] outStrings = buildInitLRN();
		int strIDX = 4;
		for (int i=0;i<exAra.length; ++i) {outStrings[i+strIDX]=exAra[i].toCSVString(dataFrmt);	}
		map.saveStrings(fileName,outStrings);		
		map.dispMessage("Finished saving .csv file with " + exAra.length+ " elements to file : "+ fileName);			
	}//save csv test data
	
	//save data in SVM record form - each record is like a map/dict -> idx: val pair.  designed for sparse data
	private void saveSVMData() {
		String[] outStrings = new String[numSmpls];
		for (int i=0;i<exAra.length; ++i) {outStrings[i]=exAra[i].toSVMString(dataFrmt);	}
		map.saveStrings(fileName,outStrings);		
		map.dispMessage("Finished saving .svm (sparse) file with " + exAra.length+ " elements to file : "+ fileName);			
	}

	//write all sphere data to appropriate files
	@Override
	public void run() {		
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
				map.setFlag(dataSavedIDX, true);	
				break;
			}
			case "sparseSVMData" : {
				saveSVMData();
				map.setFlag(dataSavedIDX, true);		
				break;
			}
			case "denseCSVData" : {				
				saveCSVData();
				map.setFlag(dataSavedIDX, true);
				break;
			}
			default :{//default to save data in lrn format
				saveLRNData();
				map.setFlag(dataSavedIDX, true);				
			}
		}
	}//run
}//straffDataWriter


