package strafford_SOM_PKG.straff_UI;

import java.io.File;
import java.util.*;

import base_SOM_Objects.*;
import base_SOM_Objects.som_ui.win_disp_ui.SOM_MapUIWin;
import base_UI_Objects.*;
import base_Utils_Objects.io.MsgCodes;
import base_Utils_Objects.vectorObjs.myPoint;
import base_Utils_Objects.vectorObjs.myVector;
import processing.core.PImage;
import strafford_SOM_PKG.straff_Features.featureCalc.Straff_WeightCalc;
import strafford_SOM_PKG.straff_SOM_Mapping.Straff_SOMMapManager;

//window that accepts trajectory editing
public class Straff_SOMMapUIWin extends SOM_MapUIWin {
	
	/**
	 * idxs of boolean values/flags - instance-specific
	 */
	public static final int 
		custExCalcedIDX					= numSOMBasePrivFlags + 0,			//whether customer prospect examples have been loaded and ftrs have been calculated or not  StraffWeightCalc.bndAra_ProdJPsIDX StraffWeightCalc.bndAra_AllJPsIDX
		tpExCalcedIDX					= numSOMBasePrivFlags + 1,			//whether true propsect examples have been loaded and ftrs have been calculated or not
		trainExCalcedIDX				= numSOMBasePrivFlags + 2,			//whether training data examples have been loaded and ftrs have been calculated or not - these are per-order training examples
		
		mapDrawPrdctFtrBMUsIDX 			= numSOMBasePrivFlags + 3,			//draw product bmu as calculated by ftrs
		mapDrawCurProdFtrBMUZoneIDX	 	= numSOMBasePrivFlags + 4,			//show currently selected prod jps' products and influence zones, as calculated by ftrs
		//non-product segments
		mapDrawNonProdJPSegIDX			= numSOMBasePrivFlags + 5,			//draw segment defined by the non-product jps present in the training examples mapped to SOM 
		mapDrawNonProdJPGroupSegIDX		= numSOMBasePrivFlags + 6,			//draw segment defined by the non-product jp groups present in the training examples mapped to SOM 
		
		//display/interaction
		mapDrawTruePspctIDX				= numSOMBasePrivFlags + 7,			//draw true prospect examples on map		
		mapDrawCustAnalysisVisIDX		= numSOMBasePrivFlags + 8,			//whether or not to draw feature calc analysis graphs for customer examples
		mapDrawTPAnalysisVisIDX			= numSOMBasePrivFlags + 9,			//whether or not to draw feature calc analysis graphs for true prospect examples
		mapDrawTrainDataAnalysisVisIDX	= numSOMBasePrivFlags + 10,			//whether or not to draw feature calc analysis graphs for training data examples (this will be relevant when training data is individual per-order-derived
						
		mapDrawCalcFtrOrAllVisIDX		= numSOMBasePrivFlags + 11,			//whether to draw calc obj for ftr-related jps, or all jps present		
		
		showSelJPIDX					= numSOMBasePrivFlags + 12, 			//if showSelRegionIDX == true, then this will show either a selected jp or jpgroup
		//train/test data managemen
		procTruProspectsIDX				= numSOMBasePrivFlags + 13,			//this will process true prospects, and load them if they haven't been loaded
		saveBMUMapsForTruPrspctsIDX		= numSOMBasePrivFlags + 14;			//this will save all the product data for the currently selected prod JP
	/**
	 * need to specify how many private state flags are in use
	 */
	private final int _numPrivFlags = numSOMBasePrivFlags + 15;			//size to set up priv flags array
	
	//	//GUI Objects	
	public final static int //offset from end of base class SOM UI objs
		uiRawDataSourceIDX 			= numSOMBaseGUIObjs + 0,			//source of raw data to be preprocced and used to train the map
		uiProdJPToDispIDX			= numSOMBaseGUIObjs + 1,			//choose current product/zone to show
		uiProdZoneDistThreshIDX		= numSOMBaseGUIObjs + 2,			//max distance from a product that a map node should be considered to be covered by that product
		uiAllJpSeenToDispIDX		= numSOMBaseGUIObjs + 3;			//choose jp to show based on all jps seen
	
	//public final int numGUIObjs = numSOMBaseGUIObjs + 4;
	
	//types of data that can be used for calc analysis 
	private int curCalcAnalysisSrcDataTypeIDX = Straff_WeightCalc.bndAra_AllJPsIDX;
	private int curCalcAnalysisJPTypeIDX = Straff_WeightCalc.bndAra_AllJPsIDX;
	
	//raw data source : 0 == csv, 1 == sql
	private int rawDataSource;
	//max sq_distance to display map nodes as under influence/influencing certain products
	private double prodZoneDistThresh;
	//////////////////////////////
	//map drawing 	draw/interaction variables
	//start location of SOM image - stX, stY, and dimensions of SOM image - width, height; locations to put calc analysis visualizations
	public float[] calcAnalysisLocs;	
	//to draw analysis results
	private float calcScale = .5f;
	//set analysisHt equal to be around 1/2 SOM_mapDims height
	private float analysisHt, analysisAllJPBarWidth, analysisPerJPWidth;
	//other PImage maps set in base class
	//array of per jpg map wts - equally weighted all jps within jpg
	private PImage[] mapPerJpgWtImgs;
	
	//which product is being shown by single-product display visualizations, as index in list of jps of products
	private int curProdToShowIDX;
	//which jp is currently being investigated of -all- jps
	private int curAllJPToShowIDX;
	
	//which nonprod jp to show (for data which support showing non-prod jps
	private int curNonProdJPToShowIDX;
	//which nonprod jpg to show (for data which support showing non-prod jpgroups
	private int curNonProdJPGroupToShowIDX;
		
	/////////
	//custom debug/function ui button names -empty will do nothing
	public String[][] menuBtnNames = new String[][] {	//each must have literals for every button defined in side bar menu, or ignored
		{"Load All Raw ---", "---","Recalc Features"},	//row 1
		{"Train Data","True Prspcts", "Prods", "SOM Cfg"},	//row 2
		{"Train->Bld Map","Map All Data", "---", "---"},	//row 3
		{"Map 1","Map 2","Map 3","Map 4"},
		{"Raw","Proced","JpJpg","MapDat","---"}	
	};
	private final String[] dfltPreBltMapNames = {"Map 1","Map 2","Map 3","Map 4"};
	//used to switch button name for 1st button to reflect whether performing csv-based load of raw data or sql query
	private String[] menuLdRawFuncBtnNames = new String[] {"CSV", "SQL"};	
	
	public Straff_SOMMapUIWin(my_procApplet _p, String _n, int _flagIdx, int[] fc, int[] sc, float[] rd, float[] rdClosed, String _winTxt, boolean _canDrawTraj) {
		super(_p, _n, _flagIdx, fc, sc, rd, rdClosed, _winTxt, _canDrawTraj);
		super.initThisWin(_canDrawTraj, true, false);
	}//ctor
	
	/**
	 * Instancing class-specific (application driven) UI buttons to display are built 
	 * in this function.  Add an entry to tmpBtnNamesArray for each button, in the order 
	 * they are to be displayed
	 * @param tmpBtnNamesArray array list of Object arrays, where in each object array : 
	 * 			the first element is the true string label, 
	 * 			the 2nd elem is false string array, and 
	 * 			the 3rd element is integer flag idx 
	 * @return total number of privBtnFlags in instancing class (including those not displayed)
	 */
	@Override
	protected final int initAllSOMPrivBtns_Indiv(ArrayList<Object[]> tmpBtnNamesArray) {
		tmpBtnNamesArray.add(new Object[] {"Hide Non-Product Job Practices","Show Non-Product Job Practices", mapDrawNonProdJPSegIDX});          
		tmpBtnNamesArray.add(new Object[] {"Hide Non-Product Job Practice Groups", "Show Non-Product Job Practice Groups", mapDrawNonProdJPGroupSegIDX});			
		tmpBtnNamesArray.add(new Object[] {"Hide Products (ftr BMUs)","Show Products (ftr BMUs)", mapDrawPrdctFtrBMUsIDX});          
		tmpBtnNamesArray.add(new Object[] {"Hide Cur Prod Zone (by ftrs)", "Show Cur Prod Zone (by ftrs)", mapDrawCurProdFtrBMUZoneIDX});	
		tmpBtnNamesArray.add(new Object[] {"Show Calc Plot on Ftr JPs", "Show Calc Plot on All JPs", mapDrawCalcFtrOrAllVisIDX});     
		tmpBtnNamesArray.add(new Object[] {"Hide Training Data Calc Plot", "Show Training Data Calc Plot", mapDrawTrainDataAnalysisVisIDX});
		tmpBtnNamesArray.add(new Object[] {"Hide Cust Prspct Calc Plot", "Show Cust Prspct Calc Plot", mapDrawCustAnalysisVisIDX});     
		tmpBtnNamesArray.add(new Object[] {"Hide True Prspct Calc Plot", "Show True Prspct Calc Plot", mapDrawTPAnalysisVisIDX});       
		tmpBtnNamesArray.add(new Object[] {"Hide True Prospects on Map", "Show True Prospects on Map", mapDrawTruePspctIDX});           
		tmpBtnNamesArray.add(new Object[] {"Mapping True Prospect BMUs", "Map True Prospect BMUs", procTruProspectsIDX});           
		tmpBtnNamesArray.add(new Object[] {"Building/Saving Tru Prspct BMUs for loaded Map","Build/Save Tru Prspct BMUs for loaded Map", saveBMUMapsForTruPrspctsIDX}); 
		return 	this._numPrivFlags;

	}//initAllSOMPrivBtns_Indiv
	/**
	 * Instance class determines the true and false labels the class-category locking should use
	 * @return array holding true(idx0) and false(idx1) labels for buttons to control display of whether 
	 * category should be locked to allow selection through within-category classes
	 */
	@Override
	protected String[] getClassCatLockBtnTFLabels() {return new String[] {"JPGroup Changes with JP","Lock JPGroup; restrict JPs to JPG"};}

	/**
	 * Instance class determines the true and false labels the class buttons use - if empty then no classes used
	 * @return array holding true(idx0) and false(idx1) labels for buttons to control display of class-based segment
	 */
	@Override
	protected final String[] getClassBtnTFLabels() {	return new String[] {"Hide Order-Based JP Segments  ","Show Order-Based JP Segments  "};}
	/**
	 * Instance class determines the true and false labels the category buttons use - if empty then no categories used
	 * @return array holding true(idx0) and false(idx1) labels for buttons to control display of category-based segment
	 */
	@Override
	protected final String[] getCategoryBtnTFLabels() {	return new String[] {"Hide Order-Based JPGroup Segments", "Show Order-Based JPGroup Segments"};}	
	
	/**
	 * This will return instance class-based true and false labels for save segment data.  if empty then no segment saving possible
	 * @return array holding true(idx0) and false(idx1) labels for buttons to control saving of segment data
	 */
	@Override
	protected final String[] getSegmentSaveBtnTFLabels() {return new String[] {"Saving Class, Category and Feature weight segment BMUs", "Save Class, Category and Feature weight segment BMUs" };}

	@Override
	protected void initMeIndiv() {
		//based on width of map
		analysisHt = (mapMgr.getMapHeight()*.45f);
		//for single jp detail display
		analysisPerJPWidth = (mapMgr.getMapWidth()*.1f);
		//default to having calc objects display analysis on ftrs 
		setPrivFlags(mapDrawCalcFtrOrAllVisIDX, true);
		//default to showing right side bar menu
		setFlags(showRightSideMenu, true);	
		//dataFrmtToUseToTrain = (int)(this.guiObjs[uiTrainDataFrmtIDX].getVal());
		prodZoneDistThresh = this.guiObjs[uiProdZoneDistThreshIDX].getVal();
		rawDataSource = (int)(this.guiObjs[uiRawDataSourceIDX].getVal());

		//moved from mapMgr ctor, to remove dependence on papplet in that object
		pa.setAllMenuBtnNames(menuBtnNames);	
		initMapAras(1, 1);
	}//initMeIndiv()
		
	@Override
	//SOM_mapDims is built by base class initMe
	protected SOM_MapManager buildMapMgr(float[] SOM_mapDims) {
		//SOM_mapDims : start x and y and dimensions of full map visualization as function of visible window size;
		//including strings for default directories specific to current project setup and Strafford
		TreeMap<String, Object> _argsMap = new TreeMap<String,Object>();
		//provide default values used by program
		_argsMap.put("configDir", "StraffordProject" + File.separator+"config" + File.separator);
		_argsMap.put("dataDir", "StraffordProject" + File.separator);
		_argsMap.put("logLevel",0);//0 is console alone,1 is log file alone, 2 is both
		
		return new Straff_SOMMapManager(SOM_mapDims, _argsMap);
	}
	
	@Override
	protected void setVisScreenDimsPriv_Indiv() {
		//now build calc analysis offset struct
		calcAnalysisLocs = new float[] {(SOM_mapLoc[0]+mapMgr.getMapWidth())*calcScale + xOff,(SOM_mapLoc[1]+mapMgr.getMapHeight())*calcScale + 10.0f};
		setAnalysisDimWidth();		
	}
	
	//per jp bar width ~= total width / # of jps
	protected void setAnalysisDimWidth() {
		analysisAllJPBarWidth = (curVisScrDims[0]/(1.0f+((Straff_SOMMapManager) mapMgr).numFtrsToShowForCalcAnalysis(curCalcAnalysisJPTypeIDX)))*.98f;	
	}
	
	//called from SOMMapUIWin base on initMapAras - this instances 2ndary maps/other instance-specific maps
	@Override
	protected void initMapArasIndiv(int w, int h, int format, int num2ndryMaps) {
		curAllJPToShowIDX = 0;
		mapPerJpgWtImgs = new PImage[num2ndryMaps];
		for(int i=0;i<mapPerJpgWtImgs.length;++i) {
			mapPerJpgWtImgs[i] = pa.createImage(w, h, format);
		}	
	}//instance-specific init 
		
	@Override
	protected void setPrivFlagsIndiv(int idx, boolean val) {
		switch (idx) {//special actions for each flag
			case mapDrawTruePspctIDX	: {//draw true prospect examples
				break;}		
			case mapDrawCalcFtrOrAllVisIDX : {
				curCalcAnalysisJPTypeIDX = (val ? Straff_WeightCalc.bndAra_ProdJPsIDX : Straff_WeightCalc.bndAra_AllJPsIDX);		
				setAnalysisDimWidth();
				break;}			
			
			case mapDrawNonProdJPSegIDX			: {//draw segments defined by non-product jps owned by training examples that mapped to each bmu
				break;}		
			case mapDrawNonProdJPGroupSegIDX	: {//draw segments defined by non-product jp groups owned by training examples that mapped to each bmu
				break;}		
			
			case mapDrawCustAnalysisVisIDX	: {//whether or not to draw feature calc analysis graphs  
				if (val) {//if setting to true then aggregate data
					setPrivFlags(mapDrawTPAnalysisVisIDX, false);
					setPrivFlags(mapDrawTrainDataAnalysisVisIDX, false);					
					curCalcAnalysisSrcDataTypeIDX= Straff_WeightCalc.custCalcObjIDX;
					((Straff_SOMMapManager) mapMgr).processCalcAnalysis(curCalcAnalysisSrcDataTypeIDX);	
					setAnalysisDimWidth();
				} else {
					
				}
				break;}
			case mapDrawTPAnalysisVisIDX	: {//whether or not to draw feature calc analysis graphs  
				if (val) {//if setting to true then aggregate data
					setPrivFlags(mapDrawCustAnalysisVisIDX, false);
					setPrivFlags(mapDrawTrainDataAnalysisVisIDX, false);					
					curCalcAnalysisSrcDataTypeIDX= Straff_WeightCalc.tpCalcObjIDX;
					((Straff_SOMMapManager) mapMgr).processCalcAnalysis(curCalcAnalysisSrcDataTypeIDX);	
					setAnalysisDimWidth();
				} else {
					
				}
				break;}
			
			case mapDrawTrainDataAnalysisVisIDX : {
				if (val) {//if setting to true then aggregate data
					setPrivFlags(mapDrawCustAnalysisVisIDX, false);
					setPrivFlags(mapDrawTPAnalysisVisIDX, false);					
					curCalcAnalysisSrcDataTypeIDX= Straff_WeightCalc.trainCalcObjIDX;
					((Straff_SOMMapManager) mapMgr).processCalcAnalysis(curCalcAnalysisSrcDataTypeIDX);	
					setAnalysisDimWidth();
				}else {
					
				}
				break;}
			case showSelJPIDX		 : {//if showSelRegionIDX == true, then this will show either a selected jp or jpgroup
				break;}
			case procTruProspectsIDX : {
//				if(val) {
//					((StraffSOMMapManager) mapMgr).buildAndSaveTrueProspectReport();	
//					addPrivBtnToClear(procTruProspectsIDX);					
//				}			
				break;}		//put true prospects on the SOM
			case saveBMUMapsForTruPrspctsIDX : {
				if(val) {
					((Straff_SOMMapManager) mapMgr).saveBMUMapsForTruPrspcts();		
					addPrivBtnToClear(saveBMUMapsForTruPrspctsIDX);			
				}				
				break;}		//save all product to prospect mappings given currently selected product jp and dist thresh
			
			case custExCalcedIDX 	: {		break;}			
			case tpExCalcedIDX  	: {		break;}		
			case trainExCalcedIDX 	: {		break;}
		}		
	}//setPrivFlagsIndiv
	/**
	 * Instance-specific code for managing locking of category segment selection to enable cycling through class within category
	 * TODO
	 * @param val whether the lock button is being turned on or off
	 */
	@Override
	protected void setPrivFlags_LockCatForClassSegs(boolean val) {
		if(val) {
			
		} else {
			
		}		
	}//setPrivFlags_LockCatForClassSegs
	
	/**
	 * Instancing class-specific (application driven) UI objects should be defined
	 * in this function.  Add an entry to tmpBtnNamesArray for each button, in the order 
	 * they are to be displayed
	 * @param tmpUIObjArray array list of Object arrays, where in each object array : 
	 * 			the first element double array of min/max/mod values
	 * 			the 2nd element is starting value
	 * 			the 3rd elem is label for object
	 * 			the 4th element is boolean array of {treat as int, has list values, value is sent to owning window}
	 * @param tmpListObjVals treemap keyed by object IDX and value is list of strings of values for all UI list select objects
	 */
	protected final void setupGUIObjsArasIndiv(ArrayList<Object[]> tmpUIObjArray, TreeMap<Integer, String[]> tmpListObjVals) {	
		//per object entry : object array of {min,max,mod},stVal,lbl,bool ara
		tmpListObjVals.put(uiRawDataSourceIDX,new String[] {"Prebuilt CSV Files","Data Tables Via SQL"});
		tmpListObjVals.put(uiProdJPToDispIDX, new String[] {"Unknown"}); 
		tmpListObjVals.put(uiAllJpSeenToDispIDX, new String[] {"Unknown"});
		
		tmpUIObjArray.add(new Object[] {new double[]{0.0, tmpListObjVals.get(uiRawDataSourceIDX).length-1, 1}, 0.0, "Raw Data Source", new boolean []{true, true, true}});		//uiRawDataSourceIDX
		tmpUIObjArray.add(new Object[] {new double[]{0.0, 260, 1.0}, 0.0, "Product JP to Show", new boolean []{true, true, true}});			//uiProdJPToDispIDX	
		tmpUIObjArray.add(new Object[] {new double[]{0.0, 5, .01}, 0.99, "Prod Max Sq Dist", new boolean []{false, false, true}});		//uiProdZoneDistThreshIDX	
		tmpUIObjArray.add(new Object[] {new double[]{0.0, 260, 1.0}, 0.0, "All JP to Show (Calc Analysis)", new boolean []{true, true, true}});			//uiAllJpSeenToDispIDX	

	}//setupGUIObjsArasIndiv
	
	/**
	 * instancing class description for category display UI object - if null or length==0 then not shown/used
	 */
	@Override
	protected String getCategoryUIObjLabel() {		return "JPGrp Segments To Show";}

	/**
	 * instancing class description for class display UI object - if null or length==0 then not shown/used
	 */
	@Override
	protected String getClassUIObjLabel() {			return "JP Segments To Show";}
	
	/**
	 * pass the list of values for the list boxes that use training jp group and jp, in idx order
	 * (ftr idx box, jp/class select and jpgroup/category select
	 * @param ftrVals
	 * @param classVals
	 * @param categoryVals
	 * @param prodVals
	 */
	public void setUI_JPFtrListVals(String[] ftrVals, String[] classVals, String[] categoryVals, String[] prodVals) {
		//lists of display strings for 
		setUI_FeatureListVals(ftrVals);
		setUI_ClassListVals(classVals);
		setUI_CategoryListVals(categoryVals);
		//set product list values
		this.guiObjs[uiProdJPToDispIDX].setListVals(prodVals);
		//in super class
		setClass_UIObj(false);
	}//setUI_JPListMaxVals
	
	
	/**
	 * pass the list of values for all jp group and jp list boxes, in idx order
	 * @param jpGrpVals
	 * @param jpVals
	 */
	public void setUI_JPAllSeenListVals(String[] jpGrpVals, String[] jpVals) {
		//refresh max size of guiobj - heavy handed, these values won't change often, and this is called -every draw frame-.
		//guiObjs[uiAllJpSeenToDispIDX].setNewMax(jpLen-1);
		guiObjs[uiAllJpSeenToDispIDX].setListVals(jpVals);
		//guiObjs[uiAllJpgSeenToDispIDX].setNewMax(jpGrpLen-1);	
	}//setUI_JPListMaxVals
	
	private boolean settingProdJPGFromJp = false, settingProdJPFromJPG = false;
	

	@Override
	protected final int getCategoryFromClass(int _curCatIDX, int _classIDX) { return ((Straff_SOMMapManager) mapMgr).getUI_JPGrpIdxFromFtrJPIdx(_classIDX, _curCatIDX);}
	//((Straff_SOMMapManager) mapMgr).getUI_JPGrpIdxFromFtrJPIdx(curMapImgIDX, _clsIDX);

	@Override
	protected final int getClassFromCategory(int _catIDX, int _curClassIDX) { return ((Straff_SOMMapManager) mapMgr).getUI_FirstJPIdxFromFtrJPGIdx(_catIDX, _curClassIDX);}

	/**
	 * return class label from index - will be instance specific
	 * @param _idx idx from class list box to get class label (used as key in map holding class data in map manager)
	 * @return
	 */
	@Override
	protected final int getClassLabelFromIDX(int _idx) {	return ((Straff_SOMMapManager) mapMgr).getFtrJpByIdx(_idx);	}
	
	
	/**
	 * return category label from index - will be instance specific
	 * @param _idx idx from category list box to get category label (used as key in map holding category data in map manager)
	 * @return
	 */
	@Override
	protected final int getCategoryLabelFromIDX(int _idx) {	return ((Straff_SOMMapManager) mapMgr).getFtrJpGroupByIdx(_idx);}
	
	@Override 
	//handle instance-specific UI components
	protected void setUIWinValsIndiv(int UIidx) {
		//if(!(settingJPGFromJp || settingProdJPGFromJp || settingClassFromCategory || settingProdJPFromJPG)) {msgObj.dispInfoMessage("SOM WIN","setUIWinVals","Idx to set  : " + UIidx);}
		switch(UIidx){
			case uiRawDataSourceIDX  : {//source of raw data
				rawDataSource = (int)(this.guiObjs[uiRawDataSourceIDX].getVal());
				//change button display
				setCustMenuBtnNames();
				msgObj.dispMessage("mySOMMapUIWin","setUIWinVals","uiRawDataSourceIDX : rawDataSource set to : " + rawDataSource, MsgCodes.info1);
				break;}					
			case uiProdJPToDispIDX : {//product to display, for product influence zones
				curProdToShowIDX = (int)guiObjs[uiProdJPToDispIDX].getVal();				
				break;}
			case uiProdZoneDistThreshIDX : {//max distance for a node to be considered a part of a product's "region" of influence		
				prodZoneDistThresh = this.guiObjs[uiProdZoneDistThreshIDX].getVal();			
				break;}
	
			case uiAllJpSeenToDispIDX		: {
				curAllJPToShowIDX = (int)guiObjs[uiAllJpSeenToDispIDX].getVal();
				break;}	
		}		
	}//setUIWinValsIndiv
	
	
	//modify menu buttons to display whether using CSV or SQL to access raw data
	@Override
	protected void setCustMenuBtnNames() {
		String rplStr = menuLdRawFuncBtnNames[(rawDataSource % menuLdRawFuncBtnNames.length)], baseStr;
		for(int i=0;i<menuBtnNames[Straff_SOMMapUISideBarMenu.btnAuxFunc1Idx].length-2;++i) {
			baseStr = (String) menuBtnNames[Straff_SOMMapUISideBarMenu.btnAuxFunc1Idx][i].subSequence(0, menuBtnNames[Straff_SOMMapUISideBarMenu.btnAuxFunc1Idx][i].length()-3);
			menuBtnNames[Straff_SOMMapUISideBarMenu.btnAuxFunc1Idx][i] = baseStr + rplStr;
		}
		//menuBtnNames[mySideBarMenu.btnAuxFunc1Idx][loadRawBtnIDX]=menuLdRawFuncBtnNames[(rawDataSource % menuLdRawFuncBtnNames.length) ];
		pa.setAllMenuBtnNames(menuBtnNames);	
	}//setCustMenuBtnNames
	
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
	protected void drawOnScreenStuffPriv(float modAmtMillis) {}

	@Override
	//draw 2d constructs over 3d area on screen - draws behind left menu section
	//modAmtMillis is in milliseconds
	protected void drawRightSideInfoBarPriv(float modAmtMillis) {
		pa.pushMatrix();pa.pushStyle();
		//display current simulation variables - call sim world through sim exec
		mapMgr.drawResultBar(pa, yOff, curPreBuiltMapIDX);
		pa.popStyle();pa.popMatrix();					
	}//drawOnScreenStuff
	
	@Override
	//set flags that should be set on each frame - these are set at beginning of frame draw
	protected void drawSetDispFlags() {
		setPrivFlags(custExCalcedIDX, ((Straff_SOMMapManager) mapMgr).isFtrCalcDone(Straff_WeightCalc.custCalcObjIDX));
		setPrivFlags(tpExCalcedIDX, ((Straff_SOMMapManager) mapMgr).isFtrCalcDone(Straff_WeightCalc.tpCalcObjIDX));	
		setPrivFlags(trainExCalcedIDX,((Straff_SOMMapManager) mapMgr).isFtrCalcDone(Straff_WeightCalc.trainCalcObjIDX));	
		//checking flag to execute if true
		if(getPrivFlags(procTruProspectsIDX)){	((Straff_SOMMapManager) mapMgr).buildAndSaveTrueProspectReport();setPrivFlags(procTruProspectsIDX,false);	}			
	}
	@Override
	//stuff to draw specific to this instance, before nodes are drawn
	protected void drawMapRectangle_Indiv(int curImgNum) {
		if(getPrivFlags(mapDrawTruePspctIDX)){			mapMgr.drawValidationData(pa);}
		boolean notDrawAnalysis = !(getPrivFlags(mapDrawCustAnalysisVisIDX) || getPrivFlags(mapDrawTPAnalysisVisIDX));
		//not drawing any analysis currently
		if (notDrawAnalysis && (mseOvrData != null)){	drawMseOverData();}//draw mouse-over info if not showing calc analysis				
		
		if (getPrivFlags( mapDrawCurProdFtrBMUZoneIDX)){		((Straff_SOMMapManager) mapMgr).drawProductRegion(pa,curProdToShowIDX,prodZoneDistThresh);}
	}//drawMapRectangleIndiv
	/**
	 * draw instance-specific per-ftr map display
	 */
	@Override
	protected void drawPerFtrMap_Indiv() {
		if(getPrivFlags(mapDrawPrdctFtrBMUsIDX)){				((Straff_SOMMapManager) mapMgr).drawProductNodes(pa, curMapImgIDX, true);}
		if(getPrivFlags(mapDrawNonProdJPSegIDX)) {	 			((Straff_SOMMapManager) mapMgr).drawNonProdJpSegments(pa,curAllJPToShowIDX);	}		
		if(getPrivFlags(mapDrawNonProdJPGroupSegIDX)) { 		((Straff_SOMMapManager) mapMgr).drawNonProdJPGroupSegments(pa,curAllJPToShowIDX);	}	
	}
	
	@Override
	/**
	 * Instancing class-specific segments to render during UMatrix display
	 */
	protected void drawSegmentsUMatrixDispIndiv() {
		if(getPrivFlags(mapDrawNonProdJPSegIDX)) {	 			((Straff_SOMMapManager) mapMgr).drawAllNonProdJpSegments(pa);}
		if(getPrivFlags(mapDrawNonProdJPGroupSegIDX)) { 		((Straff_SOMMapManager) mapMgr).drawAllNonProdJPGroupSegments(pa);}
		if(getPrivFlags(mapDrawPrdctFtrBMUsIDX)){				((Straff_SOMMapManager) mapMgr).drawAllProductNodes(pa);}
	}	
	
	@Override
	protected void drawMapIndiv() {		
		if (getPrivFlags(mapDrawCustAnalysisVisIDX)){	_drawAnalysis(custExCalcedIDX, mapDrawCustAnalysisVisIDX);	} 
		else if (getPrivFlags(mapDrawTPAnalysisVisIDX)){_drawAnalysis(tpExCalcedIDX, mapDrawTPAnalysisVisIDX);}
		else if (getPrivFlags(mapDrawTrainDataAnalysisVisIDX)) {_drawAnalysis(trainExCalcedIDX, mapDrawTrainDataAnalysisVisIDX);}
	}	
	
	private void _drawAnalysis(int exCalcedIDX, int mapDrawAnalysisIDX) {
		if (getPrivFlags(exCalcedIDX)){	
			//determine what kind of jps are being displayed 
			//int curJPIdx = ( ? curMapImgIDX : curAllJPToShowIDX);
			pa.pushMatrix();pa.pushStyle();	
			pa.translate(calcAnalysisLocs[0],SOM_mapLoc[1]*calcScale + 10,0.0f);			
			if(curCalcAnalysisJPTypeIDX == Straff_WeightCalc.bndAra_AllJPsIDX) {		//choose between displaying calc analysis of training feature jps or all jps
				((Straff_SOMMapManager) mapMgr).drawAnalysisOneJp_All(pa,analysisHt, analysisPerJPWidth,curAllJPToShowIDX, curCalcAnalysisSrcDataTypeIDX);	
				pa.popStyle();pa.popMatrix();			
				pa.pushMatrix();pa.pushStyle();
				pa.translate(rectDim[0]+5,calcAnalysisLocs[1],0.0f);					
				((Straff_SOMMapManager) mapMgr).drawAnalysisAllJps(pa, analysisHt, analysisAllJPBarWidth, curAllJPToShowIDX, curCalcAnalysisSrcDataTypeIDX);
				
			} else if(curCalcAnalysisJPTypeIDX == Straff_WeightCalc.bndAra_ProdJPsIDX)  {		
				((Straff_SOMMapManager) mapMgr).drawAnalysisOneJp_Ftr(pa,analysisHt, analysisPerJPWidth,curProdToShowIDX, curCalcAnalysisSrcDataTypeIDX);	
				pa.popStyle();pa.popMatrix();			
				pa.pushMatrix();pa.pushStyle();
				pa.translate(rectDim[0]+5,calcAnalysisLocs[1],0.0f);					
				((Straff_SOMMapManager) mapMgr).drawAnalysisFtrJps(pa, analysisHt, analysisAllJPBarWidth, curProdToShowIDX, curCalcAnalysisSrcDataTypeIDX);				
			}			
			
			pa.popStyle();pa.popMatrix();
			pa.scale(calcScale);				//scale here so that if we are drawing calc analysis, ftr map image will be shrunk
		} else {
			setPrivFlags(mapDrawAnalysisIDX, false);
		}
	}//_drawAnalysis
		
	//if launching threads for custom functions or debug, need to remove resetButtonState call in function below and call resetButtonState (with slow proc==true) when thread ends
	@Override
	protected void launchMenuBtnHndlr() {
		msgObj.dispMessage("mySOMMapUIWin","launchMenuBtnHndlr","Begin requested action", MsgCodes.info4);
		int btn = curCustBtn[curCustBtnType];
		switch(curCustBtnType) {
		case Straff_SOMMapUISideBarMenu.btnAuxFunc1Idx : {//row 1 of menu side bar buttons
			msgObj.dispMessage("mySOMMapUIWin","launchMenuBtnHndlr","Click Functions 1 in "+name+" : btn : " + btn, MsgCodes.info4);
			switch(btn){
				case 0 : {	
					//load all data from raw local csvs or sql from db
					((Straff_SOMMapManager) mapMgr).loadAndPreProcAllRawData((rawDataSource==0));//, getPrivFlags(useOnlyEvntsToTrainIDX));
					resetButtonState();
					break;}
				case 1 : {	
					resetButtonState();
					break;}
				case 2 : {	
					//recalculate currently loaded/processed data.  will reload/rebuild calc object from file, so if data is changed in format file, this will handle that.
					((Straff_SOMMapManager) mapMgr).reCalcCurrFtrs();
					resetButtonState();
					break;}
				default : {
					msgObj.dispMessage("mySOMMapUIWin","launchMenuBtnHndlr","Unknown Functions 1 btn : "+btn, MsgCodes.warning2);
					break;}
			}	
			break;}//row 1 of menu side bar buttons
	
		case Straff_SOMMapUISideBarMenu.btnAuxFunc2Idx : {//row 2 of menu side bar buttons
			msgObj.dispMessage("mySOMMapUIWin","launchMenuBtnHndlr","Click Functions 2 in "+name+" : btn : " + btn, MsgCodes.info4);//{"Ld&Bld SOM Data", "Load SOM Config", "Ld & Make Map", "Ld Prebuilt Map"},	//row 2
			//		{"Train Data","True Prspcts", "Prods", "SOM Cfg", "Func 14"},	//row 2

			switch(btn){
				case 0 : {	
					mapMgr.loadPreprocAndBuildTestTrainPartitions(getTrainTestDatPartition(), true);
					resetButtonState();
					break;}
				case 1 : {	
					((Straff_SOMMapManager) mapMgr).loadAllTrueProspectData();
					resetButtonState();
					break;}
				case 2 : {	
					//this will load all true prospects from preprocessed prospect files.
					resetButtonState();
					break;}
				case 3 : {//load all training data, default map config, and build map
					mapMgr.loadSOMConfig();//pass fraction of data to use for training
					resetButtonState();
					break;}
				default : {
					msgObj.dispMessage("mySOMMapUIWin","launchMenuBtnHndlr","Unknown Functions 2 btn : "+btn, MsgCodes.warning2);
					resetButtonState();
					break;}	
			}
			break;}//row 2 of menu side bar buttons
		case Straff_SOMMapUISideBarMenu.btnAuxFunc3Idx : {//row 3 of menu side bar buttons
			msgObj.dispMessage("mySOMMapUIWin","launchMenuBtnHndlr","Click Functions 3 in "+name+" : btn : " + btn, MsgCodes.info4);//{"Ld&Bld SOM Data", "Load SOM Config", "Ld & Make Map", "Ld Prebuilt Map"},	//row 2
			switch(btn){
				case 0 : {	
					mapMgr.loadTrainDataMapConfigAndBuildMap(false);
					resetButtonState();
					break;}
				case 1 : {	
					((Straff_SOMMapManager) mapMgr).loadAllDataAndBuildMappings();
					resetButtonState();
					break;}
				case 2 : {	
					resetButtonState();
					break;}
				case 3 : {
					resetButtonState();
					break;}
				default : {
					msgObj.dispMessage("mySOMMapUIWin","launchMenuBtnHndlr","Unknown Functions 3 btn : "+btn, MsgCodes.warning2);
					resetButtonState();
					break;}	
			}
			break;}//row 3 of menu side bar buttons
		case Straff_SOMMapUISideBarMenu.btnAuxFunc4Idx : {//row 3 of menu side bar buttons
			msgObj.dispMessage("mySOMMapUIWin","launchMenuBtnHndlr","Click Functions 3 in "+name+" : btn : " + btn, MsgCodes.info4);//{"Ld&Bld SOM Data", "Load SOM Config", "Ld & Make Map", "Ld Prebuilt Map"},	//row 2
			switch(btn){
				case 0 :
				case 1 : 
				case 2 : 
				case 3 : {//load all training data, default map config, and build map
					curPreBuiltMapIDX = btn;
					uiVals[uiMapPreBuiltDirIDX] = this.guiObjs[uiMapPreBuiltDirIDX].setVal(curPreBuiltMapIDX);
					mapMgr.loadPretrainedExistingMap(btn, true);//runs in thread, button state reset there
					resetButtonState();
					break;}
				default : {
					msgObj.dispMessage("mySOMMapUIWin","launchMenuBtnHndlr","Unknown Functions 3 btn : "+btn, MsgCodes.warning2);
					resetButtonState();
					break;}	
			}
			break;}//row 3 of menu side bar buttons
		case Straff_SOMMapUISideBarMenu.btnDBGSelCmpIdx : {//row 4 of menu side bar buttons (debug)	
			msgObj.dispMessage("mySOMMapUIWin","launchMenuBtnHndlr","Click Debug in "+name+" : btn : " + btn, MsgCodes.info4);
			//{"All->Bld Map","All Dat To Map", "Func 22", "Func 23", "Prblt Map"},	//row 3
			switch(btn){
				case 0 : {	
					((Straff_SOMMapManager) mapMgr).dbgShowAllRawData();
					resetButtonState();
					break;}
				case 1 : {	
					((Straff_SOMMapManager) mapMgr).dbgShowUniqueJPsSeen();
					((Straff_SOMMapManager) mapMgr).dbgShowCalcEqs();
					resetButtonState();
					break;}
				case 2 : {	
					((Straff_SOMMapManager) mapMgr).dbgShowJpJpgrpData();
					resetButtonState();
					break;}
				case 3 : {//show current mapdat status
					((Straff_SOMMapManager) mapMgr).dbgShowSOM_MapDat();
					resetButtonState();
					break;}
				case 4 : {						
					resetButtonState();
					break;}
				default : {
					msgObj.dispMessage("mySOMMapUIWin","launchMenuBtnHndlr","Unknown Debug btn : "+btn, MsgCodes.warning2);
					resetButtonState();
					break;}
			}				
			break;}//row 4 of menu side bar buttons (debug)			
		}		
		msgObj.dispMessage("mySOMMapUIWin","launchMenuBtnHndlr","End requested action (multithreaded actions may still be working).", MsgCodes.info4);
	}//launchMenuBtnHndlr
	
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
	public String toString(){
		String res = super.toString();
		return res;
	}


}//mySOMMapUIWin
