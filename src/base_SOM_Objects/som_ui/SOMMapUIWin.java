package base_SOM_Objects.som_ui;


import java.util.*;
import java.util.concurrent.Future;

import base_SOM_Objects.*;
import base_SOM_Objects.som_examples.*;
import base_SOM_Objects.som_vis.SOMFtrMapVisImgBuilder;
import base_UI_Objects.*;
import base_Utils_Objects.*;
import processing.core.PImage;

/**
 * base UI window functionality to be used for any SOM-based projects
 * @author john
 *
 */
public abstract class SOMMapUIWin extends myDispWindow {
	//map manager that is instanced 
	public SOMMapManager mapMgr;
	//msgObj responsible for displaying messages to console and printing to log file
	protected MessageObject msgObj;
	
	//idxs of boolean values/flags
	public static final int 
		buildSOMExe 				= 0,			//command to initiate SOM-building
		resetMapDefsIDX				= 1,			//reset default UI values for map
		mapDataLoadedIDX			= 2,			//whether map has been loaded or not	
		mapUseChiSqDistIDX			= 3,			//whether to use chi-squared (weighted by variance) distance for features or regular euclidean dist
		mapExclProdZeroFtrIDX		= 4,			//whether or not distances between two datapoints assume that absent features in source data point should be zero or ignored when comparing to map node ftrs

		//display/interaction
		mapDrawTrainDatIDX			= 5,			//draw training examples
		mapDrawTestDatIDX 			= 6,			//draw testing examples - data held out and not used to train the map 
		mapDrawNodeLblIDX			= 7,			//draw labels for nodes
		mapDrawWtMapNodesIDX		= 8,			//draw map nodes with non-0 (present) wt vals
		mapDrawPopMapNodesIDX	   	= 9,			//draw map nodes that are bmus for training examples
		mapDrawAllMapNodesIDX		= 10,			//draw all map nodes, even empty
				
		mapDrawUMatrixIDX			= 11,			//draw visualization of u matrix - distance between nodes
		mapDrawSegImgIDX			= 12,			//draw the image of the interpolated segments
		mapDrawSegMembersIDX		= 13,			//draw segments around regions of maps - visualizes clusters with different colors
		
		showSelRegionIDX			= 14,			//highlight a specific region of the map, either all nodes above a certain threshold for a chosen ftr
		//train/test data managemen
		somTrainDataLoadedIDX		= 15,			//whether data used to build map has been loaded yet
		saveLocClrImgIDX			= 16;			//
	
	public static final int numSOMBasePrivFlags = 17;
	//instancing class will determine numPrivFlags based on how many more flags are added
	
	//SOM map list options
	public String[] 
		uiMapShapeList = new String[] {"rectangular","hexagonal"},
		uiMapBndsList = new String[] {"planar","toroid"},
		uiMapKTypList = new String[] {"Dense CPU", "Dense GPU", "Sparse CPU"},
		uiMapNHoodList = new String[] {"gaussian","bubble"},
		uiMapRadClList = new String[] {"linear","exponential"},
		uiMapLrnClList = new String[] {"linear","exponential"},
		uiMapDrawExToBmuTypeList = SOMMapManager.nodeBMUMapTypes,
		uiMapTestFtrTypeList = SOMMapManager.uiMapTrainFtrTypeList,//new String[] {"Unmodified","Standardized (0->1 per ftr)","Normalized (vector mag==1)"};
		uiMapTrainFtrTypeList = SOMMapManager.uiMapTrainFtrTypeList;//new String[] {"Unmodified","Standardized (0->1 per ftr)","Normalized (vector mag==1)"};

	
	//	//GUI Objects	
	public final static int 
		uiTrainDataFrmtIDX			= 0,			//format that training data should take : unmodified, normalized or standardized
		uiTestDataFrmtIDX			= 1,			//format of vectors to use when comparing examples to nodes on map
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
		
		uiMapNodeBMUTypeToDispIDX 	= 16,			//type of examples mapping to a particular node to display in visualization
		uiNodeWtDispThreshIDX 		= 17,			//threshold for display of map nodes on individual weight maps
		uiNodeInSegThreshIDX		= 18,			//threshold of u-matrix weight for nodes to belong to same segment
		uiMseRegionSensIDX			= 19;			//senstivity threshold for mouse-over, to determine membership to a particular jp (amount a query on the map per feature needs to be to be considered part of the JP that feature represents)	
	
	public static final int numSOMBaseGUIObjs = 20;
	//instancing class will specify numGUIObjs
	
	protected double[] uiVals;				//raw values from ui components
	//threshold of wt value to display map node
	protected float mapNodeWtDispThresh;
	//type of examples using each map node as a bmu to display
	protected ExDataType mapNodeDispType;
	
	//////////////////////////////
	//map drawing 	draw/interaction variables
	public int[] dpFillClr, dpStkClr;
	
	//start location of SOM image - stX, stY, and dimensions of SOM image - width, height; locations to put calc analysis visualizations
	public float[] SOM_mapLoc, SOM_mapDims;
	
	//array of per-ftr map wts
	protected PImage[] mapPerFtrWtImgs;
	//image of umatrix (distance between nodes)
	protected PImage mapCubicUMatrixImg;
	//image of segments suggested by UMat Dist
	protected PImage mapCubicSegmentsImg;
	
	//which ftr map is currently being shown
	protected int curMapImgIDX;

	//scaling value - use this to decrease the image size and increase the scaling so it is rendered the same size
	protected static final float mapScaleVal = 10.0f;
	
	protected SOMMap_DispExample mseOvrData;//location and label of mouse-over point in map
	
	
	public SOMMapUIWin(my_procApplet _p, String _n, int _flagIdx, int[] fc, int[] sc, float[] rd, float[] rdClosed,	String _winTxt, boolean _canDrawTraj) {
		super(_p, _n, _flagIdx, fc, sc, rd, rdClosed, _winTxt, _canDrawTraj);
	}//ctor
	
	@Override
	protected final void initMe() {
		//initUIBox();				//set up ui click region to be in sidebar menu below menu's entries	
		//start x and y and dimensions of full map visualization as function of visible window size;
		float width = rectDim[3]-(2*xOff);//actually also height, but want it square, and space is wider than high, so we use height as constraint - ends up being 834.8 x 834.8 with default screen dims and without side menu
		SOM_mapDims = new float[] {width,width};
		mapMgr = buildMapMgr();
		setVisScreenWidth(rectDim[2]);
		//only set for visualization - needs to reset static refs in msgObj
		mapMgr.setPADispWinData(this, pa);
		//used to display results within this window
		msgObj = mapMgr.buildMsgObj();
		
		//this window uses right side info window
		setFlags(drawRightSideMenu, true);		//may need some re-scaling to keep things in the middle and visible
		
		//init specific sim flags
		initAllPrivFlags();		//call in instancing class when # of flags is known		
		setPrivFlags(mapDrawTrainDatIDX,false);
		setPrivFlags(mapDrawWtMapNodesIDX,false);
		setPrivFlags(mapUseChiSqDistIDX,false);
		setPrivFlags(mapDrawUMatrixIDX, true);
		setPrivFlags(mapExclProdZeroFtrIDX, true);

		dpFillClr = pa.getClr(pa.gui_White);
		dpStkClr = pa.getClr(pa.gui_Blue);	
		
		mapMgr.setCurrentTrainDataFormat((int)(this.guiObjs[uiTrainDataFrmtIDX].getVal()));
		mapMgr.setCurrentTestDataFormat((int)(this.guiObjs[uiTestDataFrmtIDX].getVal()));
		mapNodeWtDispThresh = (float)(this.guiObjs[uiNodeWtDispThreshIDX].getVal());
		mapNodeDispType = ExDataType.getVal((int)(this.guiObjs[uiMapNodeBMUTypeToDispIDX].getVal()));
		mseOvrData = null;	
		initMeIndiv();
	}//initMe()	
	
	protected abstract SOMMapManager buildMapMgr() ;
	protected abstract void initMeIndiv();
	
	
	@Override
	//initialize all private-flag based UI buttons here - called by base class
	public final void initAllPrivBtns(){
		String[] _truePrivFlagNames = new String[]{								//needs to be in order of flags
				//"Train W/Recs W/Event Data", 
				"Building SOM", "Resetting Default UI Vals", 
				"Using ChiSq for Ftr Distance", "Product Dist ignores 0-ftrs",	"Hide Train Data", "Hide Test Data",
				"Hide Node Lbls","Hide Active Ftr Map Nodes (by Wt)","Hide Map Nodes (by Pop)", "Hide Map Nodes", 
				"Showing U Mtrx Dists (Bi-Cubic)","Hide Clusters (U-Dist)", "Hide Cluster Image", 
		};
		String[] _falsePrivFlagNames = new String[]{			//needs to be in order of flags
				//"Train W/All Recs",
				"Build New Map ","Reset Default UI Vals",
				"Not Using ChiSq Distance", "Product Dist measures all ftrs","Show Train Data","Show Test Data",
				"Show Node Lbls","Show Active Ftr Map Nodes (by Wt)","Show Map Nodes (by Pop)","Show Map Nodes", 
				"Showing Per Feature Map", "Show Clusters (U-Dist)", "Show Cluster Image", 
		};
		int[] _privModFlgIdxs = new int[]{
				//useOnlyEvntsToTrainIDX, 
				buildSOMExe, resetMapDefsIDX, 
				mapUseChiSqDistIDX,mapExclProdZeroFtrIDX,mapDrawTrainDatIDX,mapDrawTestDatIDX,
				mapDrawNodeLblIDX,mapDrawWtMapNodesIDX,mapDrawPopMapNodesIDX,mapDrawAllMapNodesIDX, 
				mapDrawUMatrixIDX,mapDrawSegMembersIDX,mapDrawSegImgIDX,
		};
		initAllSOMPrivBtns_Indiv(_truePrivFlagNames, _falsePrivFlagNames, _privModFlgIdxs);
	}//initAllPrivBtns
	
	protected final void initAllPrivBtns_Final(String[] tmpTru, String[] tmpFalse, int[] tmpFlags,String[] _baseTrueNames, String[] _baseFalseNames, int[] _baseFlags) {
		
		truePrivFlagNames = new String[tmpTru.length + _baseTrueNames.length];
		System.arraycopy(_baseTrueNames, 0, truePrivFlagNames, 0, _baseTrueNames.length);
		System.arraycopy(tmpTru, 0, truePrivFlagNames, _baseTrueNames.length, tmpTru.length);
		
		falsePrivFlagNames = new String[tmpFalse.length + _baseFalseNames.length];
		System.arraycopy(_baseFalseNames, 0, falsePrivFlagNames, 0, _baseFalseNames.length);
		System.arraycopy(tmpFalse, 0, falsePrivFlagNames, _baseFalseNames.length, tmpFalse.length);
	
		
		privModFlgIdxs = new int[tmpFlags.length + _baseFlags.length];
		System.arraycopy(_baseFlags, 0, privModFlgIdxs, 0, _baseFlags.length);
		System.arraycopy(tmpFlags, 0, privModFlgIdxs, _baseFlags.length, tmpFalse.length);
		
		numClickBools = privModFlgIdxs.length;	
		//maybe have call for 		initPrivBtnRects(0);	
		initPrivBtnRects(0,numClickBools);
		
	}	
	protected abstract void initAllSOMPrivBtns_Indiv(String[] _baseTrueNames, String[] _baseFalseNames, int[] _baseFlags);
	
	//initialize structure to hold modifiable menu regions
	@Override
	protected final void setupGUIObjsAras(){		
		//need to be defined in order of declaration
		double [][] _baseGuiMinMaxModVals = new double [][]{  
			{0.0, uiMapTrainFtrTypeList.length-1, 1.0},		//uiTrainDataFrmtIDX
			{0.0, uiMapTestFtrTypeList.length-1, 1.0},		//uiTestDataFrmtIDX
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
			{0.001, 10.0, 0.001},		//uiMapLrnStIDX	 		
			{0.001, 1.0, 0.001},		//uiMapLrnEndIDX		
			{2.0, 300.0, 1.0},			//uiMapRadStIDX	 	# nodes	
			{1.0, 10.0, 1.0},			//uiMapRadEndIDX		# nodes	
			{0, uiMapDrawExToBmuTypeList.length-1, 1.0},		//uiMapNodeBMUTypeToDispIDX
			{0.0, 1.0, .01},			//uiNodeWtDispThreshIDX
			{0.0, 1.0, .001},			//uiNodeInSegThreshIDX//threshold of u-matrix weight for nodes to belong to same segment
			{0.0, 1.0, .1},				//uiMseRegionSensIDX
	
		};					
		double [] _baseGuiStVals = new double[]{	
			1,		//uiTrainDataFrmtIDX
			2,		//uiTestDataFrmtIDX
			100,	//uiTrainDatPartIDX
			10,		//uiMapRowsIDX 	 	
			10,		//uiMapColsIDX	 	
			10,		//uiMapEpochsIDX	
			0,		//uiMapShapeIDX	 	
			1,		//uiMapBndsIDX	 	
			2,		//uiMapKTypIDX	 	
			0,		//uiMapNHdFuncIDX	
			0,		//uiMapRadCoolIDX	
			0,		//uiMapLrnCoolIDX	
			1.0,	//uiMapLrnStIDX	 	
			0.1,	//uiMapLrnEndIDX	
			20.0,	//uiMapRadStIDX	 	
			1.0,	//uiMapRadEndIDX
			0,		//uiMapNodeBMUTypeToDispIDX 
			.04f,	//uiMapNodeWtDispThreshIDX
			SOMMapManager.getNodeInSegThresh(),	//uiNodeInSegThreshIDX//threshold of u-matrix weight for nodes to belong to same segment
			0,		//uiMseRegionSensIDX
		};								//starting value
		String[] _baseGuiObjNames = new String[]{
			"Train Data Frmt",			//uiTrainDataFrmtIDX
			"Data Mapping Frmt",			//uiTestDataFrmtIDX
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
			"Ex Type For Node BMU",		//uiMapNodeBMUTypeToDispIDX
			"Map Node Disp Wt Thresh",	//uiMapNodeWtDispThreshIDX
			"Segment UDist Thresh",		//uiNodeInSegThreshIDX//threshold of u-matrix weight for nodes to belong to same segment
			"Mouse Over JP Sens"		//uiMseRegionSensIDX				
		};			//name/label of component	
					
		//idx 0 is treat as int, idx 1 is obj has list vals, idx 2 is object gets sent to windows, 3 is object allows for lclick-up/rclick-down mod
		boolean [][] _baseGuiBoolVals = new boolean [][]{
			{true, true, true},			//uiTrainDataFrmtIDX
			{true, true, true},			//uiTestDataFrmtIDX
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
			{true, true, true},			//uiMapNodeBMUTypeToDispIDX
			{false, false, true}, 		//uiMapNodeWtDispThreshIDX
			{false, false, true}, 		//uiNodeInSegThreshIDX//threshold of u-matrix weight for nodes to belong to same segment
			{false, false, true},		//uiMapRegionSensIDX
		};						//per-object  list of boolean flags
		
		setupGUIObjsArasIndiv(_baseGuiMinMaxModVals, _baseGuiStVals, _baseGuiObjNames, _baseGuiBoolVals);
	}//setupGUIObjsAras
	/**
	 * need to be defined in order of declaration
	 * @param _baseGuiMinMaxModVals
	 * @param _baseGuiStVals
	 * @param _baseGuiObjNames
	 * @param _baseGuiBoolVals
	 */
	protected abstract void setupGUIObjsArasIndiv(double [][] _baseGuiMinMaxModVals, double[] _baseGuiStVals, String[] _baseGuiObjNames, boolean [][] _baseGuiBoolVals);

	//final UI setup, called from instancing class
	protected final void setupGUIObjsArasFinal( int numGUIObjs,
			double [][] _tmpGuiMinMaxModVals, double[] _tmpGuiStVals, String[] _tmpGuiObjNames, boolean [][] _tmpGuiBoolVals,
			double [][] _baseGuiMinMaxModVals, double[] _baseGuiStVals, String[] _baseGuiObjNames, boolean [][] _baseGuiBoolVals)
	{		
		guiMinMaxModVals = new double [numGUIObjs][3];
		System.arraycopy(_baseGuiMinMaxModVals, 0, guiMinMaxModVals, 0, _baseGuiMinMaxModVals.length);
		System.arraycopy(_tmpGuiMinMaxModVals, 0, guiMinMaxModVals, _baseGuiMinMaxModVals.length, _tmpGuiMinMaxModVals.length);
		
		guiStVals = new double[numGUIObjs];
		System.arraycopy(_baseGuiStVals, 0, guiStVals, 0, _baseGuiStVals.length);
		System.arraycopy(_tmpGuiStVals, 0, guiStVals, _baseGuiStVals.length, _tmpGuiStVals.length);	
		
		guiObjNames = new String[numGUIObjs];
		System.arraycopy(_baseGuiObjNames, 0, guiObjNames, 0, _baseGuiObjNames.length);
		System.arraycopy(_tmpGuiObjNames, 0, guiObjNames, _baseGuiObjNames.length, _tmpGuiObjNames.length);	
				
		guiBoolVals = new boolean [numGUIObjs][4];
		System.arraycopy(_baseGuiBoolVals, 0, guiBoolVals, 0, _baseGuiBoolVals.length);
		System.arraycopy(_tmpGuiBoolVals, 0, guiBoolVals, _baseGuiBoolVals.length, _tmpGuiBoolVals.length);	
		
		uiVals = new double[numGUIObjs];//raw values
		System.arraycopy(guiStVals, 0, uiVals, 0, numGUIObjs);
		//since horizontal row of UI comps, uiClkCoords[2] will be set in buildGUIObjs		
		guiObjs = new myGUIObj[numGUIObjs];			//list of modifiable gui objects
		if(numGUIObjs > 0){
			buildGUIObjs(guiObjNames,guiStVals,guiMinMaxModVals,guiBoolVals,new double[]{xOff,yOff});			//builds a horizontal list of UI comps
		}	
		
	}//setupGUIObjsArasFinal
	
	public void resetUIVals(){
		for(int i=0; i<guiStVals.length;++i){				guiObjs[i].setVal(guiStVals[i]);		}
	}	

	
	
	///////////////////////////////////////////
	// map image init	
	private final void reInitMapCubicSegments() {		mapCubicSegmentsImg = pa.createImage(mapCubicUMatrixImg.width,mapCubicUMatrixImg.height, pa.ARGB);}//ARGB to treat like overlay
	public final void initMapAras(int numFtrVals, int num2ndryMaps) {
		curMapImgIDX = 0;
		int format = pa.RGB; 
		int w = (int) (SOM_mapDims[0]/mapScaleVal), h = (int) (SOM_mapDims[1]/mapScaleVal);
		mapPerFtrWtImgs = new PImage[numFtrVals];
		for(int i=0;i<mapPerFtrWtImgs.length;++i) {
			mapPerFtrWtImgs[i] = pa.createImage(w, h, format);
		}		
		mapCubicUMatrixImg = pa.createImage(w, h, format);			
		reInitMapCubicSegments();
		//instancing-window specific initializations
		initMapArasIndiv(w,h, format,num2ndryMaps);
	}//initMapAras	
	
	protected abstract void initMapArasIndiv(int w, int h, int format, int num2ndFtrVals);
	
	///////////////////////////////////////////
	// end map image init		
	
	//set window-specific variables that are based on current visible screen dimensions
	protected final void setVisScreenDimsPriv() {
		float xStart = rectDim[0] + .5f*(curVisScrDims[0] - (curVisScrDims[1]-(2*xOff)));
		//start x and y and dimensions of full map visualization as function of visible window size;
		SOM_mapLoc = new float[]{xStart, rectDim[1] + yOff};
		//now build calc analysis offset struct
		setVisScreenDimsPriv_Indiv();
	}//calcAndSetMapLoc
	protected abstract void setVisScreenDimsPriv_Indiv();
	
	
	//this will be called in instancing class when # of priv flags is known - should call initPrivFlags(numPrivFlags);
	protected abstract void initAllPrivFlags();
	@Override
	public final void setPrivFlags(int idx, boolean val){
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
				mapMgr.setMapExclZeroFtrs(val);
				break;}							
			case mapDrawTrainDatIDX		: {//draw training examples
				break;}							
			case mapDrawTestDatIDX		: {//draw testing examples
				break;}		
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
			case saveLocClrImgIDX : {break;}		//save image

			default			: {setPrivFlagsIndiv(idx,val);}
		}
	}//setFlag		
	protected abstract void setPrivFlagsIndiv(int idx, boolean val);
	
	//set flag values when finished building map, to speed up initial display
	public final void setFlagsDoneMapBuild(){
		setPrivFlags(mapDrawTrainDatIDX, false);
		setPrivFlags(mapDrawWtMapNodesIDX, false);
		setPrivFlags(mapDrawAllMapNodesIDX, false);
	}//setFlagsDoneMapBuild
	
	
	
	
	//first verify that new .lrn file exists, then
	//build new SOM_MAP map using UI-entered values, then load resultant data
	protected final void buildNewSOMMap(){
		msgObj.dispMessage("mySOMMapUIWin","buildNewSOMMap","Starting Map Build", MsgCodes.info5);
		int kVal = (int)this.guiObjs[uiMapKTypIDX].getVal();
		//verify sphere train/test data exists, otherwise save it
		if(!mapMgr.mapCanBeTrained(kVal)){
			msgObj.dispMessage("mySOMMapUIWin","buildNewSOMMap","No Training Data Found, unable to build SOM", MsgCodes.warning2);
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
		for (String key : mapInts.keySet()) {msgObj.dispMessage("mySOMMapUIWin","buildNewSOMMap","mapInts["+key+"] = "+mapInts.get(key), MsgCodes.info1);}		
		HashMap<String, Float> mapFloats = new HashMap<String, Float>(); 
		mapFloats.put("mapStLrnRate",(float)this.guiObjs[uiMapLrnStIDX].getVal());
		mapFloats.put("mapEndLrnRate",(float)this.guiObjs[uiMapLrnEndIDX].getVal());
		for (String key : mapFloats.keySet()) {msgObj.dispMessage("mySOMMapUIWin","buildNewSOMMap","mapFloats["+key+"] = "+ String.format("%.4f",mapFloats.get(key)), MsgCodes.info1);}		
		HashMap<String, String> mapStrings = new HashMap<String, String> ();
		mapStrings.put("mapGridShape", getUIListValStr(uiMapShapeIDX, (int)this.guiObjs[uiMapShapeIDX].getVal()));	
		mapStrings.put("mapBounds", getUIListValStr(uiMapBndsIDX, (int)this.guiObjs[uiMapBndsIDX].getVal()));	
		mapStrings.put("mapRadCool", getUIListValStr(uiMapRadCoolIDX, (int)this.guiObjs[uiMapRadCoolIDX].getVal()));	
		mapStrings.put("mapNHood", getUIListValStr(uiMapNHdFuncIDX, (int)this.guiObjs[uiMapNHdFuncIDX].getVal()));	
		mapStrings.put("mapLearnCool", getUIListValStr(uiMapLrnCoolIDX, (int)this.guiObjs[uiMapLrnCoolIDX].getVal()));	
		for (String key : mapStrings.keySet()) {msgObj.dispMessage("mySOMMapUIWin","buildNewSOMMap","mapStrings["+key+"] = "+mapStrings.get(key), MsgCodes.info1);}
		
		//call map data object to build and execute map call
		boolean returnCode = mapMgr.buildNewSOMMap(mapInts, mapFloats, mapStrings);
		//returnCode is whether map was built and trained successfully
		setFlagsDoneMapBuild();
		msgObj.dispMessage("mySOMMapUIWin","buildNewSOMMap","Map Build " + (returnCode ? "Completed Successfully." : "Failed due to error."), MsgCodes.info5);
		setPrivFlags(buildSOMExe, false);
	}//buildNewSOMMap	

	//update UI values from passed SOM_MAPDat object's current state
	public final void setUIValues(SOM_MapDat mapDat) {
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
	
	protected final int getIDXofStringInArray(String[] ara, String tok) {
		for(int i=0;i<ara.length;++i) {			if(ara[i].equals(tok)) {return i;}		}		
		return -1;
	}
	
	protected final int getIdxFromListString(int UIidx, String dat) {
		switch(UIidx){//pa.score.staffs.size()
			case uiTrainDataFrmtIDX : {return getIDXofStringInArray(uiMapTrainFtrTypeList, dat);} 
			case uiTestDataFrmtIDX	: {return getIDXofStringInArray(uiMapTestFtrTypeList, dat);}
			case uiMapShapeIDX		: {return getIDXofStringInArray(uiMapShapeList, dat);} 
			case uiMapBndsIDX		: {return getIDXofStringInArray(uiMapBndsList, dat);} 
			case uiMapKTypIDX		: {return getIDXofStringInArray(uiMapKTypList, dat);} 
			case uiMapNHdFuncIDX	: {return getIDXofStringInArray(uiMapNHoodList, dat);} 
			case uiMapRadCoolIDX	: {return getIDXofStringInArray(uiMapRadClList, dat);} 
			case uiMapNodeBMUTypeToDispIDX : {return getIDXofStringInArray(uiMapDrawExToBmuTypeList, dat);}
			case uiMapLrnCoolIDX	: {return getIDXofStringInArray(uiMapLrnClList, dat);} 
			default : return getIdxFromListStringIndiv(UIidx, dat);
		}
	}//getIdxFromListString
	protected abstract int getIdxFromListStringIndiv(int UIidx, String dat);
	//if any ui values have a string behind them for display
	@Override
	public final String getUIListValStr(int UIidx, int validx) {		
		//msgObj.dispMessage("mySOMMapUIWin","getUIListValStr","UIidx : " + UIidx + "  Val : " + validx );
		switch(UIidx){//pa.score.staffs.size()
			case uiTrainDataFrmtIDX 		: {return uiMapTrainFtrTypeList[validx % uiMapTrainFtrTypeList.length];}
			case uiTestDataFrmtIDX			: {return uiMapTestFtrTypeList[validx % uiMapTestFtrTypeList.length];}
			case uiMapShapeIDX				: {return uiMapShapeList[validx % uiMapShapeList.length]; }
			case uiMapBndsIDX				: {return uiMapBndsList[validx % uiMapBndsList.length]; }
			case uiMapKTypIDX				: {return uiMapKTypList[validx % uiMapKTypList.length]; }
			case uiMapNHdFuncIDX			: {return uiMapNHoodList[validx % uiMapNHoodList.length]; }
			case uiMapRadCoolIDX			: {return uiMapRadClList[validx % uiMapRadClList.length]; }
			case uiMapNodeBMUTypeToDispIDX 	: {return SOMMapManager.nodeBMUMapTypes[validx %  SOMMapManager.nodeBMUMapTypes.length];}
			case uiMapLrnCoolIDX			: {return uiMapLrnClList[validx % uiMapLrnClList.length]; }	
			default 						: {return getUIListValStrIndiv(UIidx, validx);}
		}
	}//getUIListValStr
	protected abstract String getUIListValStrIndiv(int UIidx, int validx);

	@Override
	protected final void setUIWinVals(int UIidx) {
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
			case uiTrainDataFrmtIDX : {//format of training data
				mapMgr.setCurrentTrainDataFormat((int)(this.guiObjs[uiTrainDataFrmtIDX].getVal()));
				break;}
			case uiTestDataFrmtIDX : {
				mapMgr.setCurrentTestDataFormat((int)(this.guiObjs[uiTestDataFrmtIDX].getVal()));
				break;}
			case uiTrainDatPartIDX : {break;}
			case uiNodeWtDispThreshIDX : {
				mapNodeWtDispThresh = (float)(this.guiObjs[uiNodeWtDispThreshIDX].getVal());
				break;}
			case uiNodeInSegThreshIDX :{		//used to determine threshold of value for setting membership in a segment/cluster
				SOMMapManager.setNodeInSegThresh((float)(this.guiObjs[uiNodeInSegThreshIDX].getVal()));
				mapMgr.buildSegmentsOnMap();
				break;}
			case uiMapNodeBMUTypeToDispIDX : {//type of examples being mapped to each map node to display
				mapNodeDispType = ExDataType.getVal((int)(this.guiObjs[uiMapNodeBMUTypeToDispIDX].getVal()));
				break;}			
			case uiMseRegionSensIDX : {
				break;}
			default : {setUIWinValsIndiv(UIidx);}
		}
	}//setUIWinVals
	protected abstract void setUIWinValsIndiv(int UIidx);
	
	protected float getTrainTestDatPartition() {
		return (float)(.01*this.guiObjs[uiTrainDatPartIDX].getVal());
	}
	
	
	/////////////////////////////////////////
	// draw routines
	
	@Override
	protected final void drawMe(float animTimeMod) {
		drawSetDispFlags();
		setPrivFlags(mapDataLoadedIDX,mapMgr.isMapDrawable());
		drawMap();		
		if(getPrivFlags(buildSOMExe)){buildNewSOMMap();}
	}
	protected abstract void drawSetDispFlags();
	
	private void drawMap(){		
		//draw map rectangle
		pa.pushMatrix();pa.pushStyle();
		//instance-specific drawing
		drawMapIndiv();
		if(getPrivFlags(mapDataLoadedIDX)){drawMapRectangle();}	
		pa.popStyle();pa.popMatrix();
	}//drawMap()	
	protected abstract void drawMapIndiv();
	
	protected final void drawMseLocWts() {
		pa.pushMatrix();pa.pushStyle();
			pa.setFill(dpFillClr);pa.setStroke(dpStkClr);
			mseOvrData.drawMeLblMap((my_procApplet)pa);
		pa.popStyle();pa.popMatrix();		
	}
	protected final void drawMseLocFtrs() {
		pa.pushMatrix();pa.pushStyle();
			pa.setFill(dpFillClr);pa.setStroke(dpStkClr);
			mseOvrData.drawMeLblMap((my_procApplet)pa);
		pa.popStyle();pa.popMatrix();		
	}
	
	//draw map rectangle and map nodes
	protected final void drawMapRectangle() {		
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
			pa.image(tmpImg,SOM_mapLoc[0]/mapScaleVal,SOM_mapLoc[1]/mapScaleVal); if(getPrivFlags(saveLocClrImgIDX)){tmpImg.save(mapMgr.getSOMLocClrImgForFtrFName(curImgNum));  setPrivFlags(saveLocClrImgIDX,false);}			
		if(getPrivFlags(mapDrawSegImgIDX)) {pa.image(mapCubicSegmentsImg,SOM_mapLoc[0]/mapScaleVal,SOM_mapLoc[1]/mapScaleVal);}
		pa.popStyle();pa.popMatrix(); 
		pa.pushMatrix();pa.pushStyle();
		
			pa.translate(SOM_mapLoc[0],SOM_mapLoc[1],0);	
			if(getPrivFlags(mapDrawTrainDatIDX)){			mapMgr.drawTrainData(pa);}	
			if(getPrivFlags(mapDrawTestDatIDX)) {			mapMgr.drawTestData(pa);}
			//instance-specific stuff to draw on map, before nodes are drawn
			drawMapRectangleIndiv(curImgNum);
			//draw nodes
			if(getPrivFlags(mapDrawNodeLblIDX)) {
				if(getPrivFlags(mapDrawAllMapNodesIDX)){	mapMgr.drawAllNodes(pa);		} 		
				if(getPrivFlags(mapDrawPopMapNodesIDX)) {	mapMgr.drawExMapNodes(pa, mapNodeDispType);}
			} else {
				if(getPrivFlags(mapDrawAllMapNodesIDX)){	mapMgr.drawAllNodesNoLbl(pa);		} 		
				if(getPrivFlags(mapDrawPopMapNodesIDX)) {	mapMgr.drawExMapNodesNoLbl(pa, mapNodeDispType);}				
			}
	
			//if(getPrivFlags(mapDrawUMatrixIDX)) {		mapMgr.drawUMatrixVals((SOM_StraffordMain)pa);}//shows U Matrix as large blocks around nodes
			if(getPrivFlags(mapDrawSegMembersIDX)) {		mapMgr.drawSegments(pa);}
			pa.lights();
		pa.popStyle();pa.popMatrix();	
	}//drawMapRectangle
	
	protected abstract void drawMapRectangleIndiv(int curImgNum);


	/////////////////////////////////////////
	// end draw routines
	
	
	//val is 0->256
	private final int getDataClrFromFloat(Float val) {
		int ftr = Math.round(val);		
		int clrVal = ((ftr & 0xff) << 16) + ((ftr & 0xff) << 8) + (ftr & 0xff);
		return clrVal;
	}//getDataClrFromFloat
	
	//make color based on ftr value at particular index
	//jpIDX is index in feature vector we are querying
	//call this if map is trained on scaled or normed ftr data
	private final int getDataClrFromFtrVec(TreeMap<Integer, Float> ftrMap, Integer jpIDX) {
		Float ftrVal = ftrMap.get(jpIDX);
//		if(ftrVal == null) {	ftrVal=0.0f;		}
//		if (minFtrValSeen[jpIDX] > ftrVal) {minFtrValSeen[jpIDX]=ftrVal;}
//		else if (maxFtrValSeen[jpIDX] < ftrVal) {maxFtrValSeen[jpIDX]=ftrVal;}
		int ftr = 0;
		if(ftrVal != null) {	ftr = Math.round(ftrVal);		}
		int clrVal = ((ftr & 0xff) << 16) + ((ftr & 0xff) << 8) + (ftr & 0xff);
		return clrVal;
	}//getDataClrFromFtrVec
	
	//set colors of image of umatrix map
	public final void setMapUMatImgClrs() {
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
	public final void setMapSegmentImgClrs() {
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
	public final void setMapImgClrs(){ //mapRndClrImg
		float[] c;		
		int stTime = pa.millis();
		for (int i=0;i<mapPerFtrWtImgs.length;++i) {	mapPerFtrWtImgs[i].loadPixels();}//needed to retrieve pixel values
		//build uMatrix image
		setMapUMatImgClrs();
		//build segmentation image
		setMapSegmentImgClrs();
		//check if single threaded
		int numThds = mapMgr.getNumUsableThreads();
		boolean mtCapable = mapMgr.isMTCapable();
		if(mtCapable) {				
			//partition into mapMgr.numUsableThreads threads - split x values by this #, use all y values
			int numPartitions = numThds;
			int numXPerPart = mapPerFtrWtImgs[0].width / numPartitions;			
			int numXLastPart = (mapPerFtrWtImgs[0].width - (numXPerPart*numPartitions)) + numXPerPart;
			List<Future<Boolean>> mapImgFtrs = new ArrayList<Future<Boolean>>();
			List<SOMFtrMapVisImgBuilder> mapImgBuilders = new ArrayList<SOMFtrMapVisImgBuilder>();
			int[] xVals = new int[] {0,0};
			int[] yVals = new int[] {0,mapPerFtrWtImgs[0].height};
			//each thread builds columns of every map
			for (int i=0; i<numPartitions-1;++i) {	
				xVals[1] += numXPerPart;
				mapImgBuilders.add(new SOMFtrMapVisImgBuilder(mapMgr, mapPerFtrWtImgs, xVals, yVals, mapScaleVal));
				xVals[0] = xVals[1];				
			}
			//last one
			xVals[1] += numXLastPart;
			mapImgBuilders.add(new SOMFtrMapVisImgBuilder(mapMgr, mapPerFtrWtImgs, xVals, yVals, mapScaleVal));
			mapMgr.invokeSOMFtrDispBuild(mapImgBuilders);
			//try {mapImgFtrs = pa.th_exec.invokeAll(mapImgBuilders);for(Future<Boolean> f: mapImgFtrs) { f.get(); }} catch (Exception e) { e.printStackTrace(); }					
		} else {
			//single threaded exec
			for(int y = 0; y<mapPerFtrWtImgs[0].height; ++y){
				int yCol = y * mapPerFtrWtImgs[0].width;
				for(int x = 0; x < mapPerFtrWtImgs[0].width; ++x){
					int pxlIDX = x+yCol;
					c = getMapNodeLocFromPxlLoc(x, y,mapScaleVal);
					TreeMap<Integer, Float> ftrs = mapMgr.getInterpFtrs(c[0],c[1]);
					for (Integer jp : ftrs.keySet()) {mapPerFtrWtImgs[jp].pixels[pxlIDX] = getDataClrFromFtrVec(ftrs, jp);}
				}
			}
		}
		for (int i=0;i<mapPerFtrWtImgs.length;++i) {	mapPerFtrWtImgs[i].updatePixels();		}
		int endTime = pa.millis();
		msgObj.dispMessage("mySOMMapUIWin", "setMapImgClrs", "Time to build all vis imgs : "  + ((endTime-stTime)/1000.0f) + "s | Threading : " + (mtCapable ? "Multi ("+numThds+")" : "Single" ), MsgCodes.info5);
	}//setMapImgClrs

	
	//get x and y locations relative to upper corner of map
	public final float getSOMRelX (float x){return (x - SOM_mapLoc[0]);}
	public final float getSOMRelY (float y){return (y - SOM_mapLoc[1]);}
	
	
	//given pixel location relative to upper left corner of map, return map node
	protected final float[] getMapNodeLocFromPxlLoc(float mapPxlX, float mapPxlY, float sclVal){	return new float[]{(sclVal* mapPxlX * mapMgr.getNodePerPxlCol()) - .5f, (sclVal* mapPxlY * mapMgr.getNodePerPxlRow()) - .5f};}	
	//check whether the mouse is over a legitimate map location
	protected final boolean chkMouseOvr(int mouseX, int mouseY){		
		float mapMseX = getSOMRelX(mouseX), mapMseY = getSOMRelY(mouseY);//, mapLocX = mapX * mapMseX/mapDims[2],mapLocY = mapY * mapMseY/mapDims[3] ;
		if((mapMseX >= 0) && (mapMseY >= 0) && (mapMseX < SOM_mapDims[0]) && (mapMseY < SOM_mapDims[1])){
			float[] mapNLoc=getMapNodeLocFromPxlLoc(mapMseX,mapMseY, 1.0f);
			//msgObj.dispMessage("In Map : Mouse loc : " + mouseX + ","+mouseY+ "\tRel to upper corner ("+  mapMseX + ","+mapMseY +") | mapNLoc : ("+mapNLoc[0]+","+ mapNLoc[1]+")" );
			mseOvrData = getDataPointAtLoc(mapNLoc[0], mapNLoc[1], new myPointf(mapMseX, mapMseY,0));
//			int[] tmp = getDataClrAtLoc(mapNLoc[0], mapNLoc[1]);
//			msgObj.dispMessage("Color at mouse map loc :("+mapNLoc[0] + "," +mapNLoc[1]+") : " + tmp[0]+"|"+ tmp[1]+"|"+ tmp[2]);
			return true;
		} else {
			mseOvrData = null;
			return false;
		}
	}//chkMouseOvr
	//get datapoint at passed location in map coordinates (so should be in frame of map's upper right corner) - assume map is square and not hex
	protected final SOMMap_DispExample getDataPointAtLoc(float x, float y, myPointf locPt){//, boolean useScFtrs){
		float sensitivity = (float) guiObjs[uiMseRegionSensIDX].getVal();
		SOMMap_DispExample dp; 
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
	
	//return strings for directory names and for individual file names that describe the data being saved.  used for screenshots, and potentially other file saving
	//first index is directory suffix - should have identifying tags based on major/archtypical component of sim run
	//2nd index is file name, should have parameters encoded
	@Override
	protected String[] getSaveFileDirNamesPriv() {
		String dirString="", fileString ="";
		//for(int i=0;i<uiAbbrevList.length;++i) {fileString += uiAbbrevList[i]+"_"+ (uiVals[i] > 1 ? ((int)uiVals[i]) : uiVals[i] < .0001 ? String.format("%6.3e", uiVals[i]) : String.format("%3.3f", uiVals[i]))+"_";}
		return new String[]{dirString,fileString};	
	}


}//SOMMapUIWin
