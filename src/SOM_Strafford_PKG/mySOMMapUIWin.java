package SOM_Strafford_PKG;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;

import processing.core.PImage;


//window that accepts trajectory editing
public class mySOMMapUIWin extends myDispWindow {
	
	public SOMMapManager mapMgr;
	//idxs of boolean values/flags
	public static final int 
		buildSOMExe 				= 0,			//command to initiate SOM-building
		resetMapDefsIDX				= 1,			//reset default UI values for map
		examplesCalcedIDX			= 2,			//whether examples have been loaded and ftrs have been calculated or not
		mapDataLoadedIDX			= 3,			//whether map has been loaded or not	
		mapUseChiSqDistIDX			= 4,			//whether to use chi-squared (weighted by variance) distance for features or regular euclidean dist
		mapExclProdZeroFtrIDX		= 5,			//whether or not distances between two datapoints assume that absent features in source data point should be zero or ignored when comparing to map node ftrs
		mapDrawPrdctNodesIDX 		= 6,
		mapDrawCurProdZoneIDX	 	= 7,			//show currently selected prod jps' products and influence zones
		//display/interaction
		mapDrawTrainDatIDX			= 8,			//draw training examples
		mapDrawTestDatIDX 			= 9,			//draw testing examples - data held out and not used to train the map 
		mapDrawNodeLblIDX			= 10,			//draw labels for nodes
		mapDrawWtMapNodesIDX		= 11,			//draw map nodes with non-0 (present) wt vals
		mapDrawPopMapNodesIDX	   	= 12,			//draw map nodes that are bmus for training examples
		mapDrawAllMapNodesIDX		= 13,			//draw all map nodes, even empty
		mapDrawAnalysisVisIDX		= 14,			//whether or not to draw feature calc analysis graphs
		mapDrawUMatrixIDX			= 15,			//draw visualization of u matrix - distance between nodes
		mapDrawSegImgIDX			= 16,			//draw the image of the interpolated segments
		mapDrawSegMembersIDX		= 17,			//draw segments around regions of maps - visualizes clusters with different colors
		
		showSelRegionIDX			= 18,			//highlight a specific region of the map, either all nodes above a certain threshold for a chosen jp or jpgroup
		showSelJPIDX				= 19, 			//if showSelRegionIDX == true, then this will show either a selected jp or jpgroup
		//train/test data managemen
		somTrainDataLoadedIDX		= 20,			//whether data used to build map has been loaded yet
		saveLocClrImgIDX			= 21,			//
		saveProdMapsOfPrspctsIDX	= 22;			//this will save all the product data for the currently selected prod JP
	
	public static final int numPrivFlags = 23;
	
	//SOM map list options
	public String[] 
		uiRawDataSourceList = new String[] {"Prebuilt CSV Files","Data Tables Via SQL"},
		uiMapShapeList = new String[] {"rectangular","hexagonal"},
		uiMapBndsList = new String[] {"planar","toroid"},
		uiMapKTypList = new String[] {"Dense CPU", "Dense GPU", "Sparse CPU"},
		uiMapNHoodList = new String[] {"gaussian","bubble"},
		uiMapRadClList = new String[] {"linear","exponential"},
		uiMapLrnClList = new String[] {"linear","exponential"},		
		uiMapTrainFtrTypeList = SOMMapManager.uiMapTrainFtrTypeList,//new String[] {"Unmodified","Standardized (0->1 per ftr)","Normalized (vector mag==1)"};
		uiMapDrawExToBmuTypeList = SOMMapManager.nodeBMUMapTypes;
			
	//	//GUI Objects	
	public final static int 
		uiRawDataSourceIDX 			= 0,			//source of raw data to be preprocced and used to train the map
		uiTrainDataFrmtIDX			= 1,			//format that training data should take : unmodified, normalized or standardized
		uiTrainDatPartIDX			= 2,			//partition % of training data out of total data (rest is testing)
		uiMapRowsIDX 				= 3,            //map rows
		uiMapColsIDX				= 4,			//map cols
		uiMapEpochsIDX				= 5,			//# of training epochs
		uiMapShapeIDX				= 6,			//hexagonal or rectangular
		uiMapBndsIDX				= 7,			//planar or torroidal bounds
		uiMapKTypIDX				= 8,			//0 : dense cpu, 1 : dense gpu, 2 : sparse cpu.  dense needs appropriate lrn file format
		uiMapNHdFuncIDX				= 9,			//neighborhood : 0 : gaussian, 1 : bubble
		uiMapRadCoolIDX				= 10,			//radius cooling 0 : linear, 1 : exponential
		uiMapLrnCoolIDX				= 11,			//learning rate cooling 0 : linear 1 : exponential
		uiMapLrnStIDX				= 12,			//start learning rate
		uiMapLrnEndIDX				= 13,			//end learning rate
		uiMapRadStIDX				= 14,			//start radius
		uiMapRadEndIDX				= 15,			//end radius
		uiJPGToDispIDX				= 16,			//which group of jp's (a single jpg) to display on map
		uiJPToDispIDX				= 17,			//which JP Ftr IDX to display as map
		uiProdJpgToDispIDX			= 18,			//display products of this jpg
		uiProdJpToDispIDX			= 19,			//display products with this jp
		uiMapNodeBMUTypeToDispIDX 	= 20,			//type of examples mapping to a particular node to display in visualization
		uiNodeWtDispThreshIDX 		= 21,			//threshold for display of map nodes on individual weight maps
		uiNodeInSegThreshIDX		= 22,			//threshold of u-matrix weight for nodes to belong to same segment
		uiProdZoneDistThreshIDX		= 23,			//max distance a from a product that a map node should be considered to be covered by that product
		uiMseRegionSensIDX			= 24;			//senstivity threshold for mouse-over, to determine membership to a particular jp (amount a query on the map per feature needs to be to be considered part of the JP that feature represents)		
	
	public final int numGUIObjs = 25;	
	
	private double[] uiVals;				//raw values from ui components
	//threshold of wt value to display map node
	private float mapNodeWtDispThresh;
	//raw data source : 0 == csv, 1 == sql
	private int rawDataSource;
	//type of examples using each map node as a bmu to display
	private ExDataType mapNodeDispType;
	//max sq_distance to display map nodes as under influence/influencing certain products
	private double prodZoneDistThresh;
	//////////////////////////////
	//map drawing 	draw/interaction variables
	public int[] dpFillClr, dpStkClr;
	
	//start location of SOM image - stX, stY, and dimensions of SOM image - width, height; locations to put calc analysis visualizations
	public float[] SOM_mapLoc, SOM_mapDims, calcAnalysisLocs;
	
	//to draw analysis results
	private float calcScale = .5f;
	//set analysisHt equal to be around 1/2 SOM_mapDims height
	private float analysisHt, analysisAllJPBarWidth, analysisPerJPWidth;	
	//array of per-ftr map wts
	private PImage[] mapPerFtrWtImgs;
	//array of per jpg map wts - equally weighted all jps within jpg
	private PImage[] mapPerJpgWtImgs;
	//image of umatrix (distance between nodes)
	private PImage mapCubicUMatrixImg;
	//image of segments suggested by UMat Dist
	private PImage mapCubicSegmentsImg;
	
	//which map is currently being shown
	private int curMapImgIDX;
	//which product is being shown by single-product display visualizations, as index in list of jps of products
	private int curProdToShowIDX;
	//scaling value - use this to decrease the image size and increase the scaling so it is rendered the same size
	public static final float mapScaleVal = 10.0f;
	
	public DispSOMMapExample mseOvrData;//location and label of mouse-over point in map
		
	/////////
	//custom debug/function ui button names -empty will do nothing
	public String[][] menuBtnNames = new String[][] {	//each must have literals for every button defined in side bar menu, or ignored
		{"Load All Raw ---", "Load Raw Prod ---","Recalc Features"},	//row 1
		{"Ld&Bld SOM Data", "Ld SOM Config", "Func 3", "Ld Prebuilt Map"},	//row 1
		{"Show Raw","Show Proced","Dbg 3","Dbg 4","Dbg 5"}	
	};

	//used to switch button name for 1st button to reflect whether performing csv-based load of raw data or sql query
	private String[] menuLdRawFuncBtnNames = new String[] {"CSV", "SQL"};
	private int loadRawBtnIDX = 0;
	
	public mySOMMapUIWin(SOM_StraffordMain _p, String _n, int _flagIdx, int[] fc, int[] sc, float[] rd, float[] rdClosed, String _winTxt, boolean _canDrawTraj) {
		super(_p, _n, _flagIdx, fc, sc, rd, rdClosed, _winTxt, _canDrawTraj);
		float stY = rectDim[1]+rectDim[3]-4*yOff,stYFlags = stY + 2*yOff;		
		trajFillClrCnst = SOM_StraffordMain.gui_DarkCyan;	
		trajStrkClrCnst = SOM_StraffordMain.gui_Cyan;
		super.initThisWin(_canDrawTraj, true, false);
	}//ctor
	
	@Override
	//initialize all private-flag based UI buttons here - called by base class
	public void initAllPrivBtns(){
		truePrivFlagNames = new String[]{								//needs to be in order of flags
				//"Train W/Recs W/Event Data", 
				"Building SOM", "Resetting Default UI Vals", 
				"Using ChiSq for Ftr Distance", "Product Dist ignores 0-ftrs",	"Hide Train Data", "Hide Test Data",
				"Hide Node Lbls","Hide Map Nodes (by Wt)","Hide Map Nodes (by Pop)", "Hide Map Nodes", "Hide Products","Hide Cur Prod Zone",
				"Showing Per Feature Map", "Hide Clusters (U-Dist)", "Hide Cluster Image", "Hide Calc Analysis", "Saving Prospect Mappings for Products listed in config file"
		};
		falsePrivFlagNames = new String[]{			//needs to be in order of flags
				//"Train W/All Recs",
				"Build New Map ","Reset Default UI Vals",
				"Not Using ChiSq Distance", "Product Dist measures all ftrs","Show Train Data","Show Test Data",
				"Show Node Lbls","Show Map Nodes (by Wt)","Show Map Nodes (by Pop)","Show Map Nodes", "Show Products","Show Cur Prod Zone",
				"Showing U Mtrx Dists (Bi-Cubic)", "Show Clusters (U-Dist)", "Show Cluster Image", "Show Calc Analysis", "Save Prospect Mappings for Products listed in config file"
		};
		privModFlgIdxs = new int[]{
				//useOnlyEvntsToTrainIDX, 
				buildSOMExe, resetMapDefsIDX, 
				mapUseChiSqDistIDX,mapExclProdZeroFtrIDX,mapDrawTrainDatIDX,mapDrawTestDatIDX,
				mapDrawNodeLblIDX,
				mapDrawWtMapNodesIDX,mapDrawPopMapNodesIDX,mapDrawAllMapNodesIDX, mapDrawPrdctNodesIDX,mapDrawCurProdZoneIDX,
				mapDrawUMatrixIDX,mapDrawSegMembersIDX,mapDrawSegImgIDX,mapDrawAnalysisVisIDX, saveProdMapsOfPrspctsIDX};
		numClickBools = privModFlgIdxs.length;	
		//maybe have call for 		initPrivBtnRects(0);	
		initPrivBtnRects(0,numClickBools);
	}//initAllPrivBtns

	protected void initMe() {
		//initUIBox();				//set up ui click region to be in sidebar menu below menu's entries	
		//start x and y and dimensions of full map visualization as function of visible window size;
		float width = rectDim[3]-(2*xOff);//actually also height, but want it square, and space is wider than high, so we use height as constraint - ends up being 834.8 x 834.8 with default screen dims and without side menu
		SOM_mapDims = new float[] {width,width};
		mapMgr = new SOMMapManager(pa.th_exec,SOM_mapDims);
		setVisScreenWidth(rectDim[2]);
		//only set for visualization
		mapMgr.win=this;
		mapMgr.pa = pa;
		
		//based on width of map
		analysisHt = (SOM_mapDims[1]*.45f);
		//for single jp detail display
		analysisPerJPWidth = (SOM_mapDims[0]*.1f);
		
		//init specific sim flags
		initPrivFlags(numPrivFlags);			
		setPrivFlags(mapDrawTrainDatIDX,false);
		setPrivFlags(mapDrawWtMapNodesIDX,false);
		setPrivFlags(mapUseChiSqDistIDX,false);
		setPrivFlags(mapDrawUMatrixIDX, true);
		//this window uses right side info window
		setFlags(drawRightSideMenu, true);		//may need some re-scaling to keep things in the middle and visible

		setPrivFlags(mapExclProdZeroFtrIDX, true);
		mapMgr.setCurrentDataFormat((int)(this.guiObjs[uiTrainDataFrmtIDX].getVal()));
		//dataFrmtToUseToTrain = (int)(this.guiObjs[uiTrainDataFrmtIDX].getVal());
		mapNodeWtDispThresh = (float)(this.guiObjs[uiNodeWtDispThreshIDX].getVal());
		rawDataSource = (int)(this.guiObjs[uiRawDataSourceIDX].getVal());
		mapNodeDispType = ExDataType.getVal((int)(this.guiObjs[uiMapNodeBMUTypeToDispIDX].getVal()));
		prodZoneDistThresh = this.guiObjs[uiProdZoneDistThreshIDX].getVal();
		//moved from mapMgr ctor, to remove dependence on papplet in that object
		pa.setAllMenuBtnNames(menuBtnNames);	
		dpFillClr = pa.getClr(SOM_StraffordMain.gui_White);
		dpStkClr = pa.getClr(SOM_StraffordMain.gui_Blue);	
		initMapAras(1, 1);
		mseOvrData = null;					
	}//initMe
	//set window-specific variables that are based on current visible screen dimensions
	protected void setVisScreenDimsPriv() {
		float xStart = rectDim[0] + .5f*(curVisScrDims[0] - (curVisScrDims[1]-(2*xOff)));
		//start x and y and dimensions of full map visualization as function of visible window size;
		SOM_mapLoc = new float[]{xStart, rectDim[1] + yOff};
		//now build calc analysis offset struct
		calcAnalysisLocs = new float[] {(SOM_mapLoc[0]+SOM_mapDims[0])*calcScale + xOff,(SOM_mapLoc[1]+SOM_mapDims[1])*calcScale + 10.0f};
		setAnalysisDimWidth();
	}//calcAndSetMapLoc
	//per jp bar width ~= total width / # of jps
	protected void setAnalysisDimWidth() {analysisAllJPBarWidth = (curVisScrDims[0]/(1.0f+mapMgr.numFtrs));	}
	
	protected void initMapAras(int numJPVals, int numJPGVals) {
		curMapImgIDX = 0;
		int format = pa.RGB; 
		int w = (int) (SOM_mapDims[0]/mapScaleVal), h = (int) (SOM_mapDims[1]/mapScaleVal);
		mapPerFtrWtImgs = new PImage[numJPVals];
		for(int i=0;i<mapPerFtrWtImgs.length;++i) {
			mapPerFtrWtImgs[i] = pa.createImage(w, h, format);
		}		
		mapPerJpgWtImgs = new PImage[numJPGVals];
		for(int i=0;i<mapPerJpgWtImgs.length;++i) {
			mapPerJpgWtImgs[i] = pa.createImage(w, h, format);
		}		
		mapCubicUMatrixImg = pa.createImage(w, h, format);	
		
		reInitMapCubicSegments();
	}//initMapAras	
	
	private void reInitMapCubicSegments() {		mapCubicSegmentsImg = pa.createImage(mapCubicUMatrixImg.width,mapCubicUMatrixImg.height, pa.ARGB);}//ARGB to treat like overlay
	
	//set flag values when finished building map, to speed up initial display
	public void setFlagsDoneMapBuild(){
		setPrivFlags(mapDrawTrainDatIDX, false);
		//setPrivFlags(mapDrawTrDatLblIDX, false);
		setPrivFlags(mapDrawWtMapNodesIDX, false);
		setPrivFlags(mapDrawAllMapNodesIDX, false);
	}
	
	@Override
	public void setPrivFlags(int idx, boolean val){
		int flIDX = idx/32, mask = 1<<(idx%32);
		privFlags[flIDX] = (val ?  privFlags[flIDX] | mask : privFlags[flIDX] & ~mask);
		switch (idx) {//special actions for each flag
			case buildSOMExe 			: {break;}			//placeholder	
			case resetMapDefsIDX		: {if(val){resetUIVals(); setPrivFlags(resetMapDefsIDX,false);}}
			case mapDataLoadedIDX 		: {break;}			//placeholder				
			case mapUseChiSqDistIDX		: {//whether or not to use chi-squared (weighted) distance for features
				//turn off scaled ftrs if this is set
				mapMgr.setUseChiSqDist(val);
				break;}							
			case mapExclProdZeroFtrIDX		: {//whether or not distances between two datapoints assume that absent features in smaller-length datapoints are 0, or to ignore the values in the larger datapoints
				mapMgr.setFlag(mapMgr.mapExclProdZeroFtrIDX, val);
				break;}							
			case mapDrawTrainDatIDX		: {//draw training examples
				break;}							
			case mapDrawTestDatIDX		: {//draw testing examples
				break;}											
//			case mapDrawTrDatLblIDX 	: {//draw labels for training samples                                                       
//				break;}
			case mapDrawWtMapNodesIDX		: {//draw map nodes
				if (val) {//turn off other node displays
					setPrivFlags(mapDrawPopMapNodesIDX, false);
					setPrivFlags(mapDrawAllMapNodesIDX, false);					
				}
				break;}							
			case mapDrawPopMapNodesIDX  : {				
				if (val) {//turn off other node displays
					setPrivFlags(mapDrawWtMapNodesIDX, false);
					setPrivFlags(mapDrawAllMapNodesIDX, false);					
				}
				break;}
			case mapDrawAllMapNodesIDX	: {//draw all map nodes, even empty
				if (val) {//turn off other node displays
					setPrivFlags(mapDrawPopMapNodesIDX, false);
					setPrivFlags(mapDrawWtMapNodesIDX, false);					
				}
				break;}	
			case mapDrawNodeLblIDX : {//whether or not to show labels of nodes being displayed				
				break;}
			case mapDrawAnalysisVisIDX: {//whether or not to draw feature calc analysis graphs  
				if (val) {//if setting to true then aggregate data
					mapMgr.processCalcAnalysis();	
					setAnalysisDimWidth();
				}
				break;}
			case mapDrawUMatrixIDX :{//whether to show the UMatrix (distance between nodes) representation of the map - overrides per-ftr display
				break;}
			case mapDrawSegMembersIDX : {//whether to show segment membership for zones of the map, using a color overlay
				if(val) {mapMgr.buildSegmentsOnMap();}
				break;}
			case mapDrawSegImgIDX : {
				if(val) {mapMgr.buildSegmentsOnMap();}
				break;}
			case showSelRegionIDX		 : {//highlight a specific region of the map, either all nodes above a certain threshold for a chosen jp or jpgroup
				break;}
			case showSelJPIDX		 : {//if showSelRegionIDX == true, then this will show either a selected jp or jpgroup
				break;}
			case saveLocClrImgIDX : {break;}		//save image
			case saveProdMapsOfPrspctsIDX : {break;}		//save all product to prospect mappings given currently selected product jp and dist thresh
		}
	}//setFlag		
	
	//first verify that new .lrn file exists, then
	//build new SOM_MAP map using UI-entered values, then load resultant data
	protected void buildNewSOMMap(){
		mapMgr.dispMessage("mySOMMapUIWin","buildNewSOMMap","Starting Map Build", MsgCodes.info5);
		int kVal = (int)this.guiObjs[uiMapKTypIDX].getVal();
		//verify sphere train/test data exists, otherwise save it
		if(!mapMgr.mapCanBeTrained(kVal)){
			mapMgr.dispMessage("mySOMMapUIWin","buildNewSOMMap","No Training Data Found, unable to build SOM", MsgCodes.warning2);
			setPrivFlags(buildSOMExe, false);
			return;
		}
		
		//build structures used to describe map from UI inputs
		//TODO : will derive these values from config file once optimal configuration has been determined
		HashMap<String, Integer> mapInts = new HashMap<String, Integer>(); 
		mapInts.put("mapCols", (int)this.guiObjs[uiMapColsIDX].getVal());
		mapInts.put("mapRows", (int)this.guiObjs[uiMapRowsIDX].getVal());
		mapInts.put("mapEpochs", (int)this.guiObjs[uiMapEpochsIDX].getVal());
		mapInts.put("mapKType", kVal);
		mapInts.put("mapStRad", (int)this.guiObjs[uiMapRadStIDX].getVal());
		mapInts.put("mapEndRad", (int)this.guiObjs[uiMapRadEndIDX].getVal());
		for (String key : mapInts.keySet()) {mapMgr.dispMessage("mySOMMapUIWin","buildNewSOMMap","mapInts["+key+"] = "+mapInts.get(key), MsgCodes.info1);}		
		HashMap<String, Float> mapFloats = new HashMap<String, Float>(); 
		mapFloats.put("mapStLrnRate",(float)this.guiObjs[uiMapLrnStIDX].getVal());
		mapFloats.put("mapEndLrnRate",(float)this.guiObjs[uiMapLrnEndIDX].getVal());
		for (String key : mapFloats.keySet()) {mapMgr.dispMessage("mySOMMapUIWin","buildNewSOMMap","mapFloats["+key+"] = "+ String.format("%.4f",mapFloats.get(key)), MsgCodes.info1);}		
		HashMap<String, String> mapStrings = new HashMap<String, String> ();
		mapStrings.put("mapGridShape", getUIListValStr(uiMapShapeIDX, (int)this.guiObjs[uiMapShapeIDX].getVal()));	
		mapStrings.put("mapBounds", getUIListValStr(uiMapBndsIDX, (int)this.guiObjs[uiMapBndsIDX].getVal()));	
		mapStrings.put("mapRadCool", getUIListValStr(uiMapRadCoolIDX, (int)this.guiObjs[uiMapRadCoolIDX].getVal()));	
		mapStrings.put("mapNHood", getUIListValStr(uiMapNHdFuncIDX, (int)this.guiObjs[uiMapNHdFuncIDX].getVal()));	
		mapStrings.put("mapLearnCool", getUIListValStr(uiMapLrnCoolIDX, (int)this.guiObjs[uiMapLrnCoolIDX].getVal()));	
		for (String key : mapStrings.keySet()) {mapMgr.dispMessage("mySOMMapUIWin","buildNewSOMMap","mapStrings["+key+"] = "+mapStrings.get(key), MsgCodes.info1);}
		
		//call map data object to build and execute map call
		boolean returnCode = mapMgr.buildNewSOMMap(mapInts, mapFloats, mapStrings);
		//returnCode is whether map was built and trained successfully
		setFlagsDoneMapBuild();
		mapMgr.dispMessage("mySOMMapUIWin","buildNewSOMMap","Map Build " + (returnCode ? "Completed Successfully." : "Failed due to error."), MsgCodes.info5);
		setPrivFlags(buildSOMExe, false);
	}//buildNewSOMMap	

	
	//initialize structure to hold modifiable menu regions
	@Override
	protected void setupGUIObjsAras(){		
		
		guiMinMaxModVals = new double [][]{  
			{0.0, uiRawDataSourceList.length-1, 1},			//uiRawDataSourceIDX
			{0.0, uiMapTrainFtrTypeList.length-1, 1.0},		//uiTrainDataFrmtIDX
			{1.0, 100.0, 1.0},								//uiTrainDatPartIDX
			{1.0, 120.0, 10},								//uiMapRowsIDX 	 		
			{1.0, 120.0, 10},								//uiMapColsIDX	 		
			{1.0, 200.0, 10},								//uiMapEpochsIDX		
			{0.0, uiMapShapeList.length-1, 1},				//uiMapShapeIDX	 		
			{0.0, uiMapBndsList.length-1, 1},				//uiMapBndsIDX	 		
			{0.0, uiMapKTypList.length-1, .05},				//uiMapKTypIDX	 		
			{0.0, uiMapNHoodList.length-1, 1},				//uiMapNHdFuncIDX		
			{0.0, uiMapRadClList.length-1, 1},				//uiMapRadCoolIDX		
			{0.0, uiMapLrnClList.length-1, 1},				//uiMapLrnCoolIDX		
			{0.001, 1.0, 0.001},		//uiMapLrnStIDX	 		
			{0.001, 1.0, 0.001},		//uiMapLrnEndIDX		
			{2.0, 300.0, 1.0},			//uiMapRadStIDX	 	# nodes	
			{1.0, 10.0, 1.0},			//uiMapRadEndIDX		# nodes	
			{0.0, 100, 1.0},			//uiJPGToDispIDX//which group of jp's (a single jpg) to display on map - idx into list of jps
			{0.0, 260, 1.0},			//uiJPToDispIDX//which JP to display on map - idx into list of jps
			{0.0, 100, 1.0},			//uiProdJpgToDispIDX//which products to display by jpg
			{0.0, 260, 1.0},			//uiProdJpToDispIDX//which product jp to display
			{0, uiMapDrawExToBmuTypeList.length-1, 1.0},		//uiMapNodeBMUTypeToDispIDX
			{0.0, 1.0, .01},			//uiMapNodeWtDispThreshIDX
			{0.0, 1.0, .001},			//uiNodeInSegThreshIDX//threshold of u-matrix weight for nodes to belong to same segment
			{0.0, 2, .01},				//uiProdZoneDistThreshIDX
			{0.0, 1.0, .1},				//uiMseRegionSensIDX
	
		};					
		guiStVals = new double[]{	
			0,		//uiRawDataSourceIDX
			1,		//uiTrainDataFrmtIDX
			90,		//uiTrainDatPartIDX
			10,		//uiMapRowsIDX 	 	
			10,		//uiMapColsIDX	 	
			10,		//uiMapEpochsIDX	
			0,		//uiMapShapeIDX	 	
			1,		//uiMapBndsIDX	 	
			2,		//uiMapKTypIDX	 	
			0,		//uiMapNHdFuncIDX	
			0,		//uiMapRadCoolIDX	
			0,		//uiMapLrnCoolIDX	
			0.1,	//uiMapLrnStIDX	 	
			0.01,	//uiMapLrnEndIDX	
			20.0,	//uiMapRadStIDX	 	
			1.0,	//uiMapRadEndIDX
			0,      //uiJPGToDispIDX
			0,     	//uiJPToDispIDX/
			0,      //uiProdJpgToDispIDX
			0,     	//uiProdJpToDispIDX/
			0,		//uiMapNodeBMUTypeToDispIDX 
			.04f,	//uiMapNodeWtDispThreshIDX
			SOMMapManager.getNodeInSegThresh(),	//uiNodeInSegThreshIDX//threshold of u-matrix weight for nodes to belong to same segment
			0.99,	//uiProdZoneDistThreshIDX
			0,		//uiMseRegionSensIDX
		};								//starting value
		uiVals = new double[numGUIObjs];//raw values
		System.arraycopy(guiStVals, 0, uiVals, 0, numGUIObjs);
		guiObjNames = new String[]{
			"Raw Data Source", 			//uiRawDataSourceIDX
			"Train Data Frmt",			//uiTrainDataFrmtIDX
			"Data % To Train",			//uiTrainDatPartIDX
			"# Map Rows",  				//uiMapRowsIDX 	 
			"# Map Columns",  			//uiMapColsIDX	 
			"# Training Epochs",  		//uiMapEpochsIDX
			"Map Node Shape",  			//uiMapShapeIDX	 
			"Map Boundaries",  			//uiMapBndsIDX	 
			"Dense/Sparse (C/G)PU",		//uiMapKTypIDX	 
			"Neighborhood Func",  		//uiMapNHdFuncIDX
			"Radius Cooling", 			//uiMapRadCoolIDX
			"Learn rate Cooling",   	//uiMapLrnCoolIDX
			"Start Learn Rate",  		//uiMapLrnStIDX	 
			"End Learn Rate",  			//uiMapLrnEndIDX
			"Start Cool Radius",  		//uiMapRadStIDX	 
			"End Cool Radius", 			//uiMapRadEndIDX	
			"JPGrp Ftrs Shown",     	//uiJPGToDispIDX
			"JP Ftr Shown", 			//uiJPToDispIDX
			"Prod JPG to Show", 		//uiProdJpgToDispIDX  
			"Prod JP to Show",   		//uiProdJpToDispIDX
			"Ex Type For Node BMU",		//uiMapNodeBMUTypeToDispIDX
			"Map Node Disp Wt Thresh",	//uiMapNodeWtDispThreshIDX
			"Segment UDist Thresh",		//uiNodeInSegThreshIDX//threshold of u-matrix weight for nodes to belong to same segment
			"Prod Max Sq Dist",			//uiProdZoneDistThreshIDX
			"Mouse Over JP Sens"		//uiMseRegionSensIDX				
		};			//name/label of component	
					
		//idx 0 is treat as int, idx 1 is obj has list vals, idx 2 is object gets sent to windows, 3 is object allows for lclick-up/rclick-down mod
		guiBoolVals = new boolean [][]{
			{true, true, true},			//uiRawDataSourceIDX
			{true, true, true},			//uiTrainDataFrmtIDX
			{true, false, true},		//uiTrainDatPartIDX
			{true, false, true},    	//uiMapRowsIDX 	 	
			{true, false, true},    	//uiMapColsIDX	 	
			{true, false, true},    	//uiMapEpochsIDX	 	
			{true, true, true},     	//uiMapShapeIDX	 	
			{true, true, true},     	//uiMapBndsIDX	 	
			{true, true, true},     	//uiMapKTypIDX	 	
			{true, true, true},     	//uiMapNHdFuncIDX	
			{true, true, true},     	//uiMapRadCoolIDX	
			{true, true, true},     	//uiMapLrnCoolIDX	
			{false, false, true},   	//uiMapLrnStIDX	 	
			{false, false, true},   	//uiMapLrnEndIDX	 	
			{true, false, true},    	//uiMapRadStIDX	 	
			{true, false, true},    	//uiMapRadEndIDX
			{true, true, true},			//uiJPGToDispIDX	
			{true, true, true},			//uiJPToDispIDX
			{true, true, true},			//uiProdJpgToDispIDX  
			{true, true, true},			//uiProdJpToDispIDX
			{true, true, true},			//uiMapNodeBMUTypeToDispIDX
			{false, false, true}, 		//uiMapNodeWtDispThreshIDX
			{false, false, true}, 		//uiNodeInSegThreshIDX//threshold of u-matrix weight for nodes to belong to same segment
			{false, false, true}, 		//uiProdZoneDistThreshIDX
			{false, false, true},		//uiMapRegionSensIDX
		};						//per-object  list of boolean flags
		
		//since horizontal row of UI comps, uiClkCoords[2] will be set in buildGUIObjs		
		guiObjs = new myGUIObj[numGUIObjs];			//list of modifiable gui objects
		if(numGUIObjs > 0){
			buildGUIObjs(guiObjNames,guiStVals,guiMinMaxModVals,guiBoolVals,new double[]{xOff,yOff});			//builds a horizontal list of UI comps
		}
	}//setupGUIObjsAras
	
	//update UI values from passed SOM_MAPDat object's current state
	public void setUIValues(SOM_MapDat mapDat) {
		HashMap<String, Integer> mapInts = mapDat.getMapInts();
		HashMap<String, Float> mapFloats = mapDat.getMapFloats();
		HashMap<String, String> mapStrings = mapDat.getMapStrings();

		this.guiObjs[uiMapColsIDX].setVal(mapInts.get("mapCols"));
		this.guiObjs[uiMapRowsIDX].setVal(mapInts.get("mapRows"));
		this.guiObjs[uiMapEpochsIDX].setVal(mapInts.get("mapEpochs"));
		this.guiObjs[uiMapKTypIDX].setVal(mapInts.get("mapKType"));
		this.guiObjs[uiMapRadStIDX].setVal(mapInts.get("mapStRad"));
		this.guiObjs[uiMapRadEndIDX].setVal(mapInts.get("mapEndRad"));
		
		this.guiObjs[uiMapLrnStIDX].setVal(mapFloats.get("mapStLrnRate"));
		this.guiObjs[uiMapLrnEndIDX].setVal(mapFloats.get("mapEndLrnRate"));
		
		this.guiObjs[uiMapShapeIDX].setVal(getIdxFromListString(uiMapShapeIDX, mapStrings.get("mapGridShape")));	
		this.guiObjs[uiMapBndsIDX].setVal(getIdxFromListString(uiMapBndsIDX, mapStrings.get("mapBounds")));	
		this.guiObjs[uiMapRadCoolIDX].setVal(getIdxFromListString(uiMapRadCoolIDX, mapStrings.get("mapRadCool")));	
		this.guiObjs[uiMapNHdFuncIDX].setVal(getIdxFromListString(uiMapNHdFuncIDX, mapStrings.get("mapNHood")));	
		this.guiObjs[uiMapLrnCoolIDX].setVal(getIdxFromListString(uiMapLrnCoolIDX, mapStrings.get("mapLearnCool")));
				
	}//setUIValues
		
	public void resetUIVals(){
		for(int i=0; i<guiStVals.length;++i){	
			guiObjs[i].setVal(guiStVals[i]);
			//mapMgr.dispMessage("mySOMMapUIWin","resetUIVals","i:"+i+" st obj val: " + guiStVals[i]);
		}
	}
	//if any ui values have a string behind them for display
	@Override
	public String getUIListValStr(int UIidx, int validx) {		
		//mapMgr.dispMessage("mySOMMapUIWin","getUIListValStr","UIidx : " + UIidx + "  Val : " + validx );
		switch(UIidx){//pa.score.staffs.size()
			case uiRawDataSourceIDX : {return uiRawDataSourceList[validx % uiRawDataSourceList.length];}
			case uiTrainDataFrmtIDX : {return uiMapTrainFtrTypeList[validx % uiMapTrainFtrTypeList.length];}
			case uiMapShapeIDX		: {return uiMapShapeList[validx % uiMapShapeList.length]; }
			case uiMapBndsIDX		: {return uiMapBndsList[validx % uiMapBndsList.length]; }
			case uiMapKTypIDX		: {return uiMapKTypList[validx % uiMapKTypList.length]; }
			case uiMapNHdFuncIDX	: {return uiMapNHoodList[validx % uiMapNHoodList.length]; }
			case uiMapRadCoolIDX	: {return uiMapRadClList[validx % uiMapRadClList.length]; }
			case uiMapLrnCoolIDX	: {return uiMapLrnClList[validx % uiMapLrnClList.length]; }	
			case uiMapNodeBMUTypeToDispIDX : {return SOMMapManager.nodeBMUMapTypes[validx %  SOMMapManager.nodeBMUMapTypes.length];}
			case uiJPGToDispIDX		: {			return mapMgr.getJpGrpByIdxStr(validx); 		}	
			case uiJPToDispIDX		: {			return mapMgr.getJpByIdxStr(validx); 		}	
			case uiProdJpgToDispIDX		: {		return mapMgr.getProdJpGrpByIdxStr(validx); 	}	
			case uiProdJpToDispIDX		: {		return mapMgr.getProdJpByIdxStr(validx); 	}	
		}
		return "";
	}//getUIListValStr
	
	private int getIDXofStringInArray(String[] ara, String tok) {
		for(int i=0;i<ara.length;++i) {			if(ara[i].equals(tok)) {return i;}		}		
		return -1;
	}
	
	private int getIdxFromListString(int UIidx, String dat) {
		switch(UIidx){//pa.score.staffs.size()
			case uiRawDataSourceIDX : {return getIDXofStringInArray(uiRawDataSourceList, dat);}
			case uiTrainDataFrmtIDX : {return getIDXofStringInArray(uiMapTrainFtrTypeList, dat);} 
			case uiMapShapeIDX		: {return getIDXofStringInArray(uiMapShapeList, dat);} 
			case uiMapBndsIDX		: {return getIDXofStringInArray(uiMapBndsList, dat);} 
			case uiMapKTypIDX		: {return getIDXofStringInArray(uiMapKTypList, dat);} 
			case uiMapNHdFuncIDX	: {return getIDXofStringInArray(uiMapNHoodList, dat);} 
			case uiMapRadCoolIDX	: {return getIDXofStringInArray(uiMapRadClList, dat);} 
			case uiMapLrnCoolIDX	: {return getIDXofStringInArray(uiMapLrnClList, dat);} 
			case uiMapNodeBMUTypeToDispIDX : {return getIDXofStringInArray(uiMapDrawExToBmuTypeList, dat);}
		}
		return -1;
	}//getIdxFromListString
	
	public void setUI_JPListMaxVals(int jpGrpLen, int jpLen) {
		//refresh max size of guiobj - heavy handed, these values won't change often, and this is called -every draw frame-.
		guiObjs[uiJPToDispIDX].setNewMax(jpLen-1);
		guiObjs[uiJPGToDispIDX].setNewMax(jpGrpLen-1);	
	}//setUI_JPListMaxVals
	
	private boolean settingJPGFromJp = false, settingJPFromJPG = false;
	private boolean settingProdJPGFromJp = false, settingProdJPFromJPG = false;
	@Override
	protected void setUIWinVals(int UIidx) {
		double val = guiObjs[UIidx].getVal();
		if(uiVals[UIidx] != val){uiVals[UIidx] = val;} else {return;}//set values in raw array and only proceed if values have changed
		//int intVal = (int)val;
		switch(UIidx){
			case uiMapRowsIDX 	    : {guiObjs[uiMapRadStIDX].setVal(.5*Math.min(val, guiObjs[uiMapColsIDX].getVal()));break;}
			case uiMapColsIDX	    : {guiObjs[uiMapRadStIDX].setVal(.5*Math.min(guiObjs[uiMapRowsIDX].getVal(), val));break;}
			case uiMapEpochsIDX	    : {break;}
			case uiMapShapeIDX	    : {break;}
			case uiMapBndsIDX	    : {break;}
			case uiMapKTypIDX	    : {break;}
			case uiMapNHdFuncIDX	: {break;}
			case uiMapRadCoolIDX	: {break;}
			case uiMapLrnCoolIDX	: {break;}
			case uiMapLrnStIDX	    : {
				if(val <= guiObjs[uiMapLrnEndIDX].getVal()+guiMinMaxModVals[UIidx][2]) { guiObjs[UIidx].setVal(guiObjs[uiMapLrnEndIDX].getVal()+guiMinMaxModVals[UIidx][2]);}				
				break;}
			case uiMapLrnEndIDX	    : {
				if(val >= guiObjs[uiMapLrnStIDX].getVal()-guiMinMaxModVals[UIidx][2]) { guiObjs[UIidx].setVal(guiObjs[uiMapLrnStIDX].getVal()-guiMinMaxModVals[UIidx][2]);}				
				break;}
			case uiMapRadStIDX	    : {
				if(val <= guiObjs[uiMapRadEndIDX].getVal()+guiMinMaxModVals[UIidx][2]) { guiObjs[UIidx].setVal(guiObjs[uiMapRadEndIDX].getVal()+guiMinMaxModVals[UIidx][2]);}
				break;}
			case uiMapRadEndIDX	    : {
				if(val >= guiObjs[uiMapRadStIDX].getVal()-guiMinMaxModVals[UIidx][2]) { guiObjs[UIidx].setVal(guiObjs[uiMapRadStIDX].getVal()-guiMinMaxModVals[UIidx][2]);}
				break;}
			case uiJPGToDispIDX : {//highlight display of different region of SOM map corresponding to group of JPs (jpg)
				//mapMgr.dispMessage("\nSOM WIN","setUIWinVals::uiJPGToDispIDX", "Click : settingJPGFromJp : " + settingJPGFromJp);
				if(!settingJPGFromJp) {
					int curJPVal = (int)guiObjs[uiJPToDispIDX].getVal();
					int jpToSet = mapMgr.getUI_FirstJPFromJPG((int)val, curJPVal);
					//mapMgr.dispMessage("SOM WIN","setUIWinVals:uiJPGToDispIDX", "Attempt to modify uiJPToDispIDX : curJPVal : "  +curJPVal + " | jpToSet : " + jpToSet);
					settingJPFromJPG = true;
					guiObjs[uiJPToDispIDX].setVal(jpToSet);	
					setUIWinVals(uiJPToDispIDX);
					settingJPFromJPG = false;
				}
				break;}
			case uiJPToDispIDX : {//highlight display of different region of SOM map corresponding to selected JP				
				curMapImgIDX = (int)val;
				//mapMgr.dispMessage("\nSOM WIN","setUIWinVals::uiJPToDispIDX", "Click : settingJPFromJPG : " + settingJPFromJPG);
				if(!settingJPFromJPG) {
					int curJPGVal = (int)guiObjs[uiJPGToDispIDX].getVal();
					int jpgToSet = mapMgr.getUI_JPGrpFromJP(curMapImgIDX, curJPGVal);
					//mapMgr.dispMessage("SOM WIN","setUIWinVals::uiJPToDispIDX", "Attempt to modify uiJPGToDispIDX : cur JPG val : "+ curJPGVal + " | jpgToSet : " + jpgToSet);					
					settingJPGFromJp = true;
					guiObjs[uiJPGToDispIDX].setVal(jpgToSet);
					setUIWinVals(uiJPGToDispIDX);
					settingJPGFromJp = false;
				}
				//mapMgr.dispMessage("mySOMMapUIWin","setUIWinVals","uiJPToDispIDX : Setting UI JP Map to display to be idx :" + curMapImgIDX + " Corresponding to JP : " + mapMgr.getJpByIdxStr(curMapImgIDX) );					
				break;}
			
			case uiProdJpgToDispIDX		: {
				if(!settingProdJPGFromJp) {
					int curJPVal = (int)guiObjs[uiProdJpToDispIDX].getVal();
					int jpToSet = mapMgr.getUI_FirstJPFromJPG((int)val, curJPVal);
					//mapMgr.dispMessage("SOM WIN","setUIWinVals:uiJPGToDispIDX", "Attempt to modify uiJPToDispIDX : curJPVal : "  +curJPVal + " | jpToSet : " + jpToSet);
					settingProdJPFromJPG = true;
					guiObjs[uiProdJpToDispIDX].setVal(jpToSet);	
					setUIWinVals(uiProdJpToDispIDX);
					settingProdJPFromJPG = false;
				}
				break;}			
			case uiProdJpToDispIDX		: {
				curProdToShowIDX = (int)val;
				if(!settingProdJPFromJPG) {
					int curJPGVal = (int)guiObjs[uiProdJpgToDispIDX].getVal();
					int jpgToSet = mapMgr.getUI_JPGrpFromJP(curMapImgIDX, curJPGVal);
					//mapMgr.dispMessage("SOM WIN","setUIWinVals::uiJPToDispIDX", "Attempt to modify uiJPGToDispIDX : cur JPG val : "+ curJPGVal + " | jpgToSet : " + jpgToSet);					
					settingProdJPGFromJp = true;
					guiObjs[uiProdJpgToDispIDX].setVal(jpgToSet);
					setUIWinVals(uiProdJpgToDispIDX);
					settingProdJPGFromJp = false;
				}
				break;}	
			case uiTrainDataFrmtIDX : {//format of training data
				mapMgr.setCurrentDataFormat((int)(this.guiObjs[uiTrainDataFrmtIDX].getVal()));
				break;}
			case uiRawDataSourceIDX  : {//source of raw data
				rawDataSource = (int)(this.guiObjs[uiRawDataSourceIDX].getVal());
				//change button display
				setCustMenuBtnNames();
				mapMgr.dispMessage("mySOMMapUIWin","setUIWinVals","uiRawDataSourceIDX : rawDataSource : " + rawDataSource, MsgCodes.info1);
				break;}			
			case uiMapNodeBMUTypeToDispIDX : {//type of examples being mapped to each map node to display
				mapNodeDispType = ExDataType.getVal((int)(this.guiObjs[uiMapNodeBMUTypeToDispIDX].getVal()));
				break;}
			case uiNodeWtDispThreshIDX : {
				mapNodeWtDispThresh = (float)(this.guiObjs[uiNodeWtDispThreshIDX].getVal());
				break;}
			case uiNodeInSegThreshIDX :{		//used to determine threshold of value for setting membership in a segment/cluster
				SOMMapManager.setNodeInSegThresh((float)(this.guiObjs[uiNodeInSegThreshIDX].getVal()));
				mapMgr.buildSegmentsOnMap();
				break;}
			case uiProdZoneDistThreshIDX : {//max distance for a node to be considered a part of a product's "region" of influence		
				prodZoneDistThresh = this.guiObjs[uiProdZoneDistThreshIDX].getVal();			
				break;}
			case uiMseRegionSensIDX : {
				break;}
		}
	}//setUIWinVals
	//modify menu buttons to display whether using CSV or SQL to access raw data
	private void setCustMenuBtnNames() {
		String rplStr = menuLdRawFuncBtnNames[(rawDataSource % menuLdRawFuncBtnNames.length)], baseStr;
		for(int i=0;i<menuBtnNames[mySideBarMenu.btnAuxFunc1Idx].length-1;++i) {
			baseStr = (String) menuBtnNames[mySideBarMenu.btnAuxFunc1Idx][i].subSequence(0, menuBtnNames[mySideBarMenu.btnAuxFunc1Idx][i].length()-3);
			menuBtnNames[mySideBarMenu.btnAuxFunc1Idx][i] = baseStr + rplStr;
		}
		//menuBtnNames[mySideBarMenu.btnAuxFunc1Idx][loadRawBtnIDX]=menuLdRawFuncBtnNames[(rawDataSource % menuLdRawFuncBtnNames.length) ];
		pa.setAllMenuBtnNames(menuBtnNames);	
	}
	
	//save prospects covered by currenlty selected prodJP on ui
	private void saveAllProspectsForCurJP() {
		mapMgr.saveProductToProspectsMappings(prodZoneDistThresh);
		//mapMgr.saveProspectsForProdJP(curProdToShowIDX, prodZoneDistThresh);
		setPrivFlags(saveProdMapsOfPrspctsIDX, false);
	}
	
	//get x and y locations relative to upper corner of map
	public float getSOMRelX (float x){return (x - SOM_mapLoc[0]);}
	public float getSOMRelY (float y){return (y - SOM_mapLoc[1]);}
	

	//given pixel location relative to upper left corner of map, return map node
	public float[] getMapNodeLocFromPxlLoc(float mapPxlX, float mapPxlY, float sclVal){	return new float[]{(sclVal* mapPxlX * mapMgr.getNodePerPxlCol()) - .5f, (sclVal* mapPxlY * mapMgr.getNodePerPxlRow()) - .5f};}	
	//check whether the mouse is over a legitimate map location
	public boolean chkMouseOvr(int mouseX, int mouseY){		
		float mapMseX = getSOMRelX(mouseX), mapMseY = getSOMRelY(mouseY);//, mapLocX = mapX * mapMseX/mapDims[2],mapLocY = mapY * mapMseY/mapDims[3] ;
		if((mapMseX >= 0) && (mapMseY >= 0) && (mapMseX < SOM_mapDims[0]) && (mapMseY < SOM_mapDims[1])){
			float[] mapNLoc=getMapNodeLocFromPxlLoc(mapMseX,mapMseY, 1.0f);
			//mapMgr.dispMessage("In Map : Mouse loc : " + mouseX + ","+mouseY+ "\tRel to upper corner ("+  mapMseX + ","+mapMseY +") | mapNLoc : ("+mapNLoc[0]+","+ mapNLoc[1]+")" );
			mseOvrData = getDataPointAtLoc(mapNLoc[0], mapNLoc[1], new myPointf(mapMseX, mapMseY,0));
//			int[] tmp = getDataClrAtLoc(mapNLoc[0], mapNLoc[1]);
//			mapMgr.dispMessage("Color at mouse map loc :("+mapNLoc[0] + "," +mapNLoc[1]+") : " + tmp[0]+"|"+ tmp[1]+"|"+ tmp[2]);
			return true;
		} else {
			mseOvrData = null;
			return false;
		}
	}//chkMouseOvr
	//get datapoint at passed location in map coordinates (so should be in frame of map's upper right corner) - assume map is square and not hex
	public DispSOMMapExample getDataPointAtLoc(float x, float y, myPointf locPt){//, boolean useScFtrs){
		float sensitivity = (float) guiObjs[uiMseRegionSensIDX].getVal();
		DispSOMMapExample dp;
		if (this.getPrivFlags(mapDrawUMatrixIDX)) {
			float dist = mapMgr.getBiCubicInterpUMatVal(x, y);
			dp = mapMgr.buildTmpDataExampleDists(locPt, dist, sensitivity);			
		} else {
			TreeMap<Integer, Float> ftrs = mapMgr.getInterpFtrs(x,y);
			if(ftrs == null) {return null;} 
			dp = mapMgr.buildTmpDataExampleFtrs(locPt, ftrs, sensitivity);
		}
		dp.setMapLoc(locPt);
		return dp;
	}//getDataPointAtLoc	
	
//	//make color based on ftr value at particular index
//	//jpIDX is index in feature vector we are querying
//	//call this if map is trained on unmodified data
//	private int getDataClrFromFtrVecUnModded(TreeMap<Integer, Float> ftrMap, Integer jpIDX) {		
//		Float ftrVal = ftrMap.get(jpIDX);
//		int ftr = 0;
//		if(ftrVal != null) {			ftr = mapMgr.scaleUnfrmttedFtrData(ftrVal, jpIDX); 		}
//		int clrVal = ((ftr & 0xff) << 16) + ((ftr & 0xff) << 8) + (ftr & 0xff);
//		return clrVal;
//	}//getDataClrFromFtrVec
	
	//val is 0->256
	private int getDataClrFromFloat(Float val) {
		int ftr = Math.round(val);		
		int clrVal = ((ftr & 0xff) << 16) + ((ftr & 0xff) << 8) + (ftr & 0xff);
		return clrVal;
	}
	
	//make color based on ftr value at particular index
	//jpIDX is index in feature vector we are querying
	//call this if map is trained on scaled or normed ftr data
	private int getDataClrFromFtrVec(TreeMap<Integer, Float> ftrMap, Integer jpIDX) {
		Float ftrVal = ftrMap.get(jpIDX);
//		if(ftrVal == null) {	ftrVal=0.0f;		}
//		if (minFtrValSeen[jpIDX] > ftrVal) {minFtrValSeen[jpIDX]=ftrVal;}
//		else if (maxFtrValSeen[jpIDX] < ftrVal) {maxFtrValSeen[jpIDX]=ftrVal;}
		int ftr = 0;
		if(ftrVal != null) {	ftr = Math.round(ftrVal);		}
		int clrVal = ((ftr & 0xff) << 16) + ((ftr & 0xff) << 8) + (ftr & 0xff);
		return clrVal;
	}//getDataClrFromFtrVec
	
	@Override
	public void initDrwnTrajIndiv(){}
	@Override
	public void drawCustMenuObjs(){}
	@Override
	protected boolean simMe(float modAmtSec) {return false;}
	//set camera to custom location - only used if dispFlag set
	@Override
	protected void setCameraIndiv(float[] camVals){}
	@Override
	protected void stopMe() {}	
	
	@Override
	protected void drawOnScreenStuffPriv(float modAmtMillis) {
		
	}

	@Override
	//draw 2d constructs over 3d area on screen - draws behind left menu section
	//modAmtMillis is in milliseconds
	protected void drawRightSideInfoBarPriv(float modAmtMillis) {
		pa.pushMatrix();pa.pushStyle();
		//display current simulation variables - call sim world through sim exec
		mapMgr.drawResultBar(pa, yOff);
		pa.popStyle();pa.popMatrix();					
	}//drawOnScreenStuff

	@Override
	protected void drawMe(float animTimeMod) {
		setPrivFlags(examplesCalcedIDX, mapMgr.isFtrCalcDone());
		setPrivFlags(mapDataLoadedIDX,mapMgr.isMapDrawable());
		drawMap();	
		if(getPrivFlags(saveProdMapsOfPrspctsIDX)) {saveAllProspectsForCurJP();}
		if(getPrivFlags(buildSOMExe)){buildNewSOMMap();}
	}
	private void drawMseLocWts() {
		pa.pushMatrix();pa.pushStyle();
			pa.setFill(dpFillClr);pa.setStroke(dpStkClr);
			mseOvrData.drawMeLblMap(pa);
		pa.popStyle();pa.popMatrix();		
	}
	private void drawMseLocJPs() {
		pa.pushMatrix();pa.pushStyle();
			pa.setFill(dpFillClr);pa.setStroke(dpStkClr);
			mseOvrData.drawMeLblMap(pa);
		pa.popStyle();pa.popMatrix();		
	}
		
	//draw map rectangle and map nodes
	private void drawMapRectangle() {
		pa.pushMatrix();pa.pushStyle();
			pa.noLights();
			pa.scale(mapScaleVal);
			PImage tmpImg;
			int curImgNum;
			if(getPrivFlags(mapDrawUMatrixIDX)) {				
				tmpImg =  mapCubicUMatrixImg;
				curImgNum = -1;
			} else {
				tmpImg = mapPerFtrWtImgs[curMapImgIDX];		
				curImgNum = curMapImgIDX;
			}
			pa.image(tmpImg,SOM_mapLoc[0]/mapScaleVal,SOM_mapLoc[1]/mapScaleVal); if(getPrivFlags(saveLocClrImgIDX)){tmpImg.save(mapMgr.getSOMLocClrImgForJPFName(curImgNum));  setPrivFlags(saveLocClrImgIDX,false);}			
		if(getPrivFlags(mapDrawSegImgIDX)) {pa.image(mapCubicSegmentsImg,SOM_mapLoc[0]/mapScaleVal,SOM_mapLoc[1]/mapScaleVal);}
		pa.popStyle();pa.popMatrix();
		
		pa.pushMatrix();pa.pushStyle();
			pa.translate(SOM_mapLoc[0],SOM_mapLoc[1],0);	
			if(getPrivFlags(mapDrawTrainDatIDX)){			mapMgr.drawTrainData(pa);}	
			if(getPrivFlags(mapDrawTestDatIDX)) {			mapMgr.drawTestData(pa);}
			if(getPrivFlags(mapDrawNodeLblIDX)) {
				if(getPrivFlags(mapDrawAllMapNodesIDX)){	mapMgr.drawAllNodes(pa);		} 		
				if(getPrivFlags(mapDrawPopMapNodesIDX)) {	mapMgr.drawExMapNodes(pa, mapNodeDispType);}
			} else {
				if(getPrivFlags(mapDrawAllMapNodesIDX)){	mapMgr.drawAllNodesNoLbl(pa);		} 		
				if(getPrivFlags(mapDrawPopMapNodesIDX)) {	mapMgr.drawExMapNodesNoLbl(pa, mapNodeDispType);}				
			}
			//draw nodes
			if (curImgNum > -1) {
				if ((!getPrivFlags(mapDrawAnalysisVisIDX)) && (mseOvrData != null)){	drawMseLocJPs();}//draw mouse-over info if not showing calc analysis				
				if(getPrivFlags(mapDrawPrdctNodesIDX)){		mapMgr.drawProductNodes(pa, curMapImgIDX, true);}
				if(getPrivFlags(mapDrawWtMapNodesIDX)){		mapMgr.drawNodesWithWt(pa, mapNodeWtDispThresh, curMapImgIDX);} 
			} else {//draw all products				
				if ((!getPrivFlags(mapDrawAnalysisVisIDX)) && (mseOvrData != null)){	drawMseLocWts();}
				if(getPrivFlags(mapDrawPrdctNodesIDX)){		mapMgr.drawAllProductNodes(pa);}
			}
			
			if (getPrivFlags( mapDrawCurProdZoneIDX)){		mapMgr.drawProductRegion(pa,curProdToShowIDX,prodZoneDistThresh);}
			//if(getPrivFlags(mapDrawUMatrixIDX)) {		mapMgr.drawUMatrixVals(pa);}//shows U Matrix as large blocks around nodes
			if(getPrivFlags(mapDrawSegMembersIDX)) {		mapMgr.drawSegments(pa);}
			pa.lights();
		pa.popStyle();pa.popMatrix();		
	}//drawMapRectangle
	
	private void drawMap(){		
		//draw map rectangle
		pa.pushMatrix();pa.pushStyle();
		if (getPrivFlags(mapDrawAnalysisVisIDX)){
			if (getPrivFlags(examplesCalcedIDX)){	
				pa.pushMatrix();pa.pushStyle();	
				pa.translate(calcAnalysisLocs[0],SOM_mapLoc[1]*calcScale + 10,0.0f);
				//pa.setFill(new int[] {0,255,0}, 255);
	//			pa.rect(0,0,analysisPerJPWidth,analysisHt);
				mapMgr.drawAnalysisOneJp(pa,analysisHt, analysisPerJPWidth,curMapImgIDX);	
				pa.popStyle();pa.popMatrix();
				
				pa.pushMatrix();pa.pushStyle();
				pa.translate(rectDim[0]+5,calcAnalysisLocs[1],0.0f);
	//			pa.setFill(new int[] {255,0,0}, 255);
	//			pa.rect(0,0,analysisAllJPBarWidth*mapMgr.numFtrs,analysisHt);
				mapMgr.drawAnalysisAllJps(pa, analysisHt, analysisAllJPBarWidth, curMapImgIDX);
				pa.popStyle();pa.popMatrix();
				pa.scale(calcScale);				//scale here so that if we are drawing calc analysis, ftr map image will be shrunk
			} else {
				setPrivFlags(mapDrawAnalysisVisIDX, false);
			}
		}		
		if(getPrivFlags(mapDataLoadedIDX)){drawMapRectangle();}	
		pa.popStyle();pa.popMatrix();
	}//drawMap()	
	
	
	//set colors of image of umatrix map
	public void setMapUMatImgClrs() {
		mapCubicUMatrixImg.loadPixels();
		float[] c;	
		//mapUMatrixImg
		//single threaded exec
		for(int y = 0; y<mapCubicUMatrixImg.height; ++y){
			int yCol = y * mapCubicUMatrixImg.width;
			for(int x = 0; x < mapCubicUMatrixImg.width; ++x){
				c = getMapNodeLocFromPxlLoc(x, y,mapScaleVal);
				Float valC = mapMgr.getBiCubicInterpUMatVal(c[0],c[1]);
				mapCubicUMatrixImg.pixels[x+yCol] = getDataClrFromFloat(valC);
			}
		}
		mapCubicUMatrixImg.updatePixels();	
	}//setMapUMatImgClrs
	//set colors of image of umatrix map
	public void setMapSegmentImgClrs() {
		reInitMapCubicSegments();//reinitialize map array
		mapCubicSegmentsImg.loadPixels();
		float[] c;	
		//single threaded exec
		for(int y = 0; y<mapCubicSegmentsImg.height; ++y){
			int yCol = y * mapCubicSegmentsImg.width;
			for(int x = 0; x < mapCubicSegmentsImg.width; ++x){
				c = getMapNodeLocFromPxlLoc(x, y,mapScaleVal);
				int valC = mapMgr.getSegementColorAtPxl(c[0],c[1]);
				mapCubicSegmentsImg.pixels[x+yCol] = valC;
			}
		}
		mapCubicSegmentsImg.updatePixels();
	}//setMapUMatImgClrs
	
	//sets colors of background image of map -- partition pxls for each thread
	public void setMapImgClrs(){ //mapRndClrImg
		float[] c;		
		int stTime = pa.millis();
		for (int i=0;i<mapPerFtrWtImgs.length;++i) {	mapPerFtrWtImgs[i].loadPixels();}
		//build uMatrix image
		setMapUMatImgClrs();
		//build segmentation image
		setMapSegmentImgClrs();
		//if single threaded
		int numThds = mapMgr.getNumUsableThreads();
		boolean mtCapable = mapMgr.isMTCapable();
		if(mtCapable) {				
			//partition into mapMgr.numUsableThreads threads - split x values by this #, use all y values
			int numPartitions = numThds;
			int numXPerPart = mapPerFtrWtImgs[0].width / numPartitions;			
			int numXLastPart = (mapPerFtrWtImgs[0].width - (numXPerPart*numPartitions)) + numXPerPart;
			List<Future<Boolean>> mapImgFtrs = new ArrayList<Future<Boolean>>();
			List<straffMapVisImgBuilder> mapImgBuilders = new ArrayList<straffMapVisImgBuilder>();
			int[] xVals = new int[] {0,0};
			int[] yVals = new int[] {0,mapPerFtrWtImgs[0].height};
			
			for (int i=0; i<numPartitions-1;++i) {	
				xVals[1] += numXPerPart;
				mapImgBuilders.add(new straffMapVisImgBuilder(mapMgr, mapPerFtrWtImgs, xVals, yVals, mapScaleVal));
				xVals[0] = xVals[1];				
			}
			//last one
			xVals[1] += numXLastPart;
			mapImgBuilders.add(new straffMapVisImgBuilder(mapMgr, mapPerFtrWtImgs, xVals, yVals, mapScaleVal));
			try {mapImgFtrs = pa.th_exec.invokeAll(mapImgBuilders);for(Future<Boolean> f: mapImgFtrs) { f.get(); }} catch (Exception e) { e.printStackTrace(); }					
		} else {
			//single threaded exec
			for(int y = 0; y<mapPerFtrWtImgs[0].height; ++y){
				int yCol = y * mapPerFtrWtImgs[0].width;
				for(int x = 0; x < mapPerFtrWtImgs[0].width; ++x){
					c = getMapNodeLocFromPxlLoc(x, y,mapScaleVal);
					TreeMap<Integer, Float> ftrs = mapMgr.getInterpFtrs(c[0],c[1]);
					for (Integer jp : ftrs.keySet()) {mapPerFtrWtImgs[jp].pixels[x+yCol] = getDataClrFromFtrVec(ftrs, jp);}
				}
			}
		}
		for (int i=0;i<mapPerFtrWtImgs.length;++i) {	mapPerFtrWtImgs[i].updatePixels();		}
		int endTime = pa.millis();
		mapMgr.dispMessage("mySOMMapUIWin", "setMapImgClrs", "Time to build all vis imgs : "  + ((endTime-stTime)/1000.0f) + "s | Threading : " + (mtCapable ? "Multi ("+numThds+")" : "Single" ), MsgCodes.info5);
	}//setMapImgClrs
		
	//return strings for directory names and for individual file names that describe the data being saved.  used for screenshots, and potentially other file saving
	//first index is directory suffix - should have identifying tags based on major/archtypical component of sim run
	//2nd index is file name, should have parameters encoded
	@Override
	protected String[] getSaveFileDirNamesPriv() {
		String dirString="", fileString ="";
		//for(int i=0;i<uiAbbrevList.length;++i) {fileString += uiAbbrevList[i]+"_"+ (uiVals[i] > 1 ? ((int)uiVals[i]) : uiVals[i] < .0001 ? String.format("%6.3e", uiVals[i]) : String.format("%3.3f", uiVals[i]))+"_";}
		return new String[]{dirString,fileString};	
	}
	
	//if launching threads for custom functions or debug, need to remove resetButtonState call in function below and call resetButtonState (with slow proc==true) when thread ends
	@Override
	protected void launchMenuBtnHndlr() {
		int btn = curCustBtn[curCustBtnType];
		switch(curCustBtnType) {
		case mySideBarMenu.btnAuxFunc1Idx : {
			mapMgr.dispMessage("mySOMMapUIWin","launchMenuBtnHndlr","Click Functions 1 in "+name+" : btn : " + btn, MsgCodes.info4);
			switch(btn){
				case 0 : {	
					//load all data from raw local csvs or sql from db
					mapMgr.loadAllRawData((rawDataSource==0));//, getPrivFlags(useOnlyEvntsToTrainIDX));
					break;}
				case 1 : {	
					//load product data from raw local csvs or sql from db
					mapMgr.loadRawProductData((rawDataSource==0));
					resetButtonState();
					break;}
				case 2 : {	
					//recalculate currently loaded/processed data.  will reload/rebuild calc object from file, so if data is changed in format file, this will handle that.
					mapMgr.calcFtrsDiffsMinsAndSave();
					resetButtonState();
					break;}
				default : {
					mapMgr.dispMessage("mySOMMapUIWin","launchMenuBtnHndlr","Unknown Functions 1 btn : "+btn, MsgCodes.warning2);
					break;}
			}	
			break;}//row 1 of menu side bar buttons
		case mySideBarMenu.btnAuxFunc2Idx : {
			mapMgr.dispMessage("mySOMMapUIWin","launchMenuBtnHndlr","Click Functions 2 in "+name+" : btn : " + btn, MsgCodes.info4);//{"Ld&Bld SOM Data", "Load SOM Config", "Ld & Make Map", "Ld Prebuilt Map"},	//row 1
			switch(btn){
				case 0 : {	
					mapMgr.loadPreprocAndBuildTestTrainPartitions((float)(.01*this.guiObjs[uiTrainDatPartIDX].getVal()));
					resetButtonState();
					break;}
				case 1 : {	
					mapMgr.loadSOMConfig();//pass fraction of data to use for training
					resetButtonState();
					break;}
				case 2 : {	
					resetButtonState();
					break;}
				case 3 : {	
					mapMgr.loadPretrainedExistingMap();
					break;}
				default : {
					mapMgr.dispMessage("mySOMMapUIWin","launchMenuBtnHndlr","Unknown Functions 2 btn : "+btn, MsgCodes.warning2);
					break;}	
			}
			break;}//row 2 of menu side bar buttons
		case mySideBarMenu.btnDBGSelCmpIdx : {
			mapMgr.dispMessage("mySOMMapUIWin","launchMenuBtnHndlr","Click Debug in "+name+" : btn : " + btn, MsgCodes.info4);
			switch(btn){
				case 0 : {	
					mapMgr.dbgShowAllRawData();
					resetButtonState();
					break;}//verify priority queue functionality
				case 1 : {	
					mapMgr.dbgShowUniqueJPsSeen();
					mapMgr.dbgShowCalcEqs();
					mapMgr.dbgShowAllFtrVecs();
					resetButtonState();
					break;}//verify FEL pq integrity
				case 2 : {	
					resetButtonState();
					break;}
				case 3 : {	
					resetButtonState();
					break;}
				case 4 : {						
					resetButtonState();
					break;}
				default : {
					mapMgr.dispMessage("mySOMMapUIWin","launchMenuBtnHndlr","Unknown Debug btn : "+btn, MsgCodes.warning2);
					break;}
			}				
			break;}//row 3 of menu side bar buttons (debug)			
		}		
	}//launchMenuBtnHndlr
	
		
	
	private void toggleDbgBtn(int idx, boolean val) {
		setPrivFlags(idx, !getPrivFlags(idx));
	}
	
	//handle mouseover 
	@Override
	protected boolean hndlMouseMoveIndiv(int mouseX, int mouseY, myPoint mseClckInWorld){
		boolean res = false;
		if(getPrivFlags(mapDataLoadedIDX)){ res = chkMouseOvr(mouseX, mouseY);	}
		return res;
	}	
	@Override
	protected boolean hndlMouseClickIndiv(int mouseX, int mouseY, myPoint mseClckInWorld, int mseBtn) {
		boolean mod = false;
		
		
		if(mod) {return mod;}
		else {return checkUIButtons(mouseX, mouseY);}
	}
	@Override
	protected boolean hndlMouseDragIndiv(int mouseX, int mouseY,int pmouseX, int pmouseY, myPoint mouseClickIn3D, myVector mseDragInWorld, int mseBtn) {
		boolean mod = false;
		
		return mod;
	}
	@Override
	protected void hndlMouseRelIndiv() {	}	
	@Override
	public void hndlFileLoadIndiv(String[] vals, int[] stIdx) {//TODO manage this directly in map manager
		
	}

	@Override
	public ArrayList<String> hndlFileSaveIndiv() {//TODO manage this directly in map manager
		ArrayList<String> res = new ArrayList<String>();

		return res;
	}
	
	@Override
	protected myPoint getMsePtAs3DPt(int mouseX, int mouseY){return pa.P(mouseX,mouseY,0);}
	@Override
	protected void snapMouseLocs(int oldMouseX, int oldMouseY, int[] newMouseLoc){}//not a snap-to window
	@Override
	protected void processTrajIndiv(myDrawnSmplTraj drawnNoteTraj){		}
	@Override
	protected void endShiftKeyI() {}
	@Override
	protected void endAltKeyI() {}
	@Override
	protected void endCntlKeyI() {}
	@Override
	protected void addSScrToWinIndiv(int newWinKey){}
	@Override
	protected void addTrajToScrIndiv(int subScrKey, String newTrajKey){}
	@Override
	protected void delSScrToWinIndiv(int idx) {}	
	@Override
	protected void delTrajToScrIndiv(int subScrKey, String newTrajKey) {}
	//resize drawn all trajectories
	@Override
	protected void resizeMe(float scale) {		
		//any resizing done
	}
	@Override
	protected void closeMe() {}
	@Override
	protected void showMe() {
		//pa.setMenuDbgBtnNames(menuDbgBtnNames);	
		setCustMenuBtnNames();
	}
	@Override
	public String toString(){
		String res = super.toString();
		return res;
	}


}//myTrajEditWin
