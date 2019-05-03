package base_UI_Objects;

import java.io.File;
import java.util.*;

import base_Utils_Objects.*;
import processing.core.*;

//abstract class to hold base code for a menu/display window (2D for gui, etc), to handle displaying and controlling the window, and calling the implementing class for the specifics
public abstract class myDispWindow {
	public my_procApplet pa;
	public static int winCnt = 0;
	public int ID;	
	public String name, winText;		
	public int[] fillClr, strkClr, rtSideUIFillClr, rtSideUIStrkClr;
	public int trajFillClrCnst, trajStrkClrCnst;
	public float[] rectDim, closeBox, rectDimClosed, mseClickCrnr;	
	//current visible screen width and height
	public float[] curVisScrDims;

	public static final float xOff = 20 , yOff = 18.0f * (my_procApplet.txtSz/12.0f), btnLblYOff = 2 * yOff, rowStYOff = yOff*.15f;
	private static final float maxBtnWidthMult = .95f;
	public static final int topOffY = 40;			//offset values to render boolean menu on side of screen - offset at top before drawing
	public static final float clkBxDim = 10;//size of interaction/close window box in pxls
	
	public int pFlagIdx;					//the flags idx in the PApplet that controls this window - use -1 for none	
	private int[] dispFlags;	
	public static final int 
				showIDX 			= 0,			//whether or not to show this window
				is3DWin 			= 1,
				canChgView			= 2,			//view can change
				isRunnable 			= 3,			//runs a simulation
				closeable 			= 4,			//window is able to be closed
				hasScrollBars 		= 5,			//this window has scroll bars (both vert and horizontal)
				canDrawTraj 		= 6,			//whether or not this window will accept a drawn trajectory
				drawingTraj 		= 7,			//whether a trajectory is being drawn in this window - all windows handle trajectory input, has different functions in each window
				editingTraj 		= 8,			//whether a trajectory is being edited in this window
				showTrajEditCrc 	= 9,			//set this when some editing mechanism has taken place - draw a circle of appropriate diameter at mouse and shrink it quickly, to act as visual cue
				smoothTraj 			= 10,			//trajectory has been clicked nearby, time to smooth
				trajDecays 			= 11,			//drawn trajectories eventually/immediately disappear
				trajPointsAreFlat 	= 12,			//trajectory drawn points are flat (for pick, to prevent weird casting collisions				
				procMouseMove 		= 13,
				mouseSnapMove		= 14,			//mouse locations for this window are discrete multiples - if so implement inherited function to calculate mouse snap location
				uiObjMod			= 15,			//a ui object in this window has been modified
				useRndBtnClrs		= 16,	
				useCustCam			= 17,			//whether or not to use a custom camera for this window
				drawMseEdge			= 18,			//whether or not to draw the mouse location/edge from eye/projection onto box
				drawRightSideMenu	= 19,			//whether this window has a right-side info menu overlay
				showRightSideMenu	= 20,			//whether this window is currently showing right side info menu, or if it is minimized
				clearPrivBtns		= 21;			//momentary priv buttons have been set, need to be cleared next frame
						
	public static final int numDispFlags = 22;
	
	//private window-specific flags and UI components (buttons)
	public int[] privFlags;
	public String[] truePrivFlagNames; //needs to be in order of flags	
	public String[] falsePrivFlagNames;//needs to be in order of flags
	
		//for boolean buttons based on child-class window specific values
	public int[][] privFlagColors;
	public int[] privModFlgIdxs;										//only modifiable idx's will be shown as buttons - this needs to be in order of flag names
	public float[][] privFlagBtns;									//clickable dimensions for these buttons
	public int numClickBools;
	//array of priv buttons to be cleared next frame - should always be empty except when buttons need to be cleared
	protected ArrayList<Integer> privBtnsToClear;
	
	//edit circle quantities for visual cues when grab and smoothen
	public static final int[] editCrcFillClrs = new int[] {my_procApplet.gui_FaintMagenta, my_procApplet.gui_FaintGreen};			
	public static final float[] editCrcRads = new float[] {20.0f,40.0f};			
	public static final float[] editCrcMods = new float[] {1f,2f};			
	public final myPoint[] editCrcCtrs = new myPoint[] {new myPoint(0,0,0),new myPoint(0,0,0)};			
	public float[] editCrcCurRads = new float[] {0,0};	
	
	//UI objects in this window
	//GUI Objects
	public myGUIObj[] guiObjs;	
	public int msClkObj, msOvrObj;												//myGUIObj object that was clicked on  - for modification, object mouse moved over
	public int msBtnClcked;														//mouse button clicked
	public float[] uiClkCoords;												//subregion of window where UI objects may be found
	public static final double uiWidthMult = 9;							//multipler of size of label for width of UI components, when aligning components horizontally
	
	public double[][] guiMinMaxModVals;					//min max mod values
	public double[] guiStVals;							//starting values
	public String[] guiObjNames;							//display labels for UI components	
	//idx 0 is treat as int, idx 1 is obj has list vals, idx 2 is object gets sent to windows
	public boolean[][] guiBoolVals;						//array of UI flags for UI objects
	
	//offset to bottom of custom window menu 
	protected float custMenuOffset;
	
	//box holding x,y,w,h values of black rectangle to form around menu for display variables on right side of screen, if present
	private float[] UIRtSideRectBox;
	//closed window box
	private float[] closedUIRtSideRecBox;

	//drawn trajectory
	public myDrawnSmplTraj tmpDrawnTraj;						//currently drawn curve and all handling code - send to instanced owning screen

	//all trajectories in this particular display window - String key is unique identifier for what component trajectory is connected to
	public TreeMap<Integer,TreeMap<String,ArrayList<myDrawnSmplTraj>>> drwnTrajMap;				
	
	//ara to hold uniquely-identifying strings for each trajectory-receiving component
	public String[] trajNameAra;		
	
	public int numSubScrInWin = 2;									//# of subscreens in a window.  will generally be 1, but with sequencer will have at least 2 (piano roll and score view)
	public int[] numTrajInSubScr;									//# of trajectories available for each sub screen
	public static final int 
				traj1IDX = 0,
				traj2IDX = 1;
	
	public int curDrnTrajScrIDX;									//currently used/shown drawn trajectory - 1st idx (which screen)
	public int curTrajAraIDX;										//currently used/shown drawn trajectory - 2nd idx (which staff trajectory applies to)

	public int[][] drawTrajBoxFillClrs;
	
	///////////
	//display and camera related variables - managed per window
	public static final float TWO_PI =(float) (Math.PI*2.0f), HALF_PI =(float) (Math.PI/2.0f);
	protected float dz=0, rx=(float) (-0.06f*TWO_PI), ry=-0.04f*TWO_PI;		// distance to camera. Manipulated with wheel or when,view angles manipulated when space pressed but not mouse	
	public final float camInitialDist = -200,		//initial distance camera is from scene - needs to be negative
			camInitRy = ry,
			camInitRx = rx;

	protected myVector focusTar;							//target of focus - used in translate to set where the camera is looking - allow for modification
	protected myVector sceneFcsVal;							//set this value  to be default target of focus	- don't programmatically change, keep to use as reset
	protected myPoint sceneCtrVal;							//set this value to be different display center translations -to be used to calculate mouse offset in world for pick
	
	//to control how much is shown in the window - if stuff extends off the screen and for 2d window
	public myScrollBars[] scbrs;
	
	private final int[] trueBtnClr = new int[]{220,255,220,255}, falseBtnClr = new int[]{255,215,215,255};
	
	//path to save screenshots for this dispwindow
	protected final String ssPathBase;
	
	//these ints hold the index of which custom functions or debug functions should be launched.  
	//these are set when the sidebar menu is clicked and these processes are requested, and they are set to -1 when these processes are launched.  this is so the buttons can be turned on before the process starts
	protected int[] curCustBtn = new int[] {-1,-1,-1};// curCustFunc = -1, curCustDbg = -1;
	protected int curCustBtnType = -1;//type/row of current button selected
	//this is set to true when curCustXXX vals are set to != -1; this is used as a 1-frame buffer to allow the UI to turn on the source buttons of these functions
	private boolean custClickSetThisFrame = false, custFuncDoLaunch = false;
	
	public myDispWindow(my_procApplet _p, String _n, int _flagIdx, int[] fc,  int[] sc, float[] rd, float[] rdClosed, String _winTxt, boolean _canDrawTraj) {
		pa=_p;
		ID = winCnt++;
		name = _n;
		pFlagIdx = _flagIdx;
		//base screenshot path
		String tmpNow = pa.now.toInstant().toString();
		tmpNow = tmpNow.replace(':','_');
		ssPathBase = pa.sketchPath() +File.separatorChar +name+"_"+tmpNow + File.separatorChar;
		initClrDims( fc, sc, rd, rdClosed);
		winText = _winTxt;
		trajFillClrCnst = my_procApplet.gui_Black;		//override this in the ctor of the instancing window class
		trajStrkClrCnst = my_procApplet.gui_Black;		
		msClkObj = -1;
		msOvrObj = -1;
	}	
	
	public void initThisWin(boolean _canDrawTraj, boolean _trajIsFlat, boolean _isMenu){
		initTmpTrajStuff(_trajIsFlat);	
		initFlags();	
		setFlags(canDrawTraj, _canDrawTraj);
		setFlags(trajPointsAreFlat, _trajIsFlat);
		//setFlags(closeable, true);
		//setFlags(drawMseEdge,true);
		if(!_isMenu){
			initUIBox();				//set up ui click region to be in sidebar menu below menu's entries - do not do here for sidebar
		}
		curTrajAraIDX = 0;		
		setupGUIObjsAras();				//setup all ui objects and record final y value in sidebar menu for UI Objects in this window
		
		privBtnsToClear = new ArrayList<Integer>();
		initAllPrivBtns();
		initMe();
		
		setClosedBox();
		mseClickCrnr = new float[2];		//this is offset for click to check buttons in x and y - since buttons for all menus will be in menubar, this should be the upper left corner of menubar - upper left corner of rect 
		mseClickCrnr[0] = 0;
		mseClickCrnr[1] = 0;		
		if(getFlags(hasScrollBars)){scbrs = new myScrollBars[numSubScrInWin];	for(int i =0; i<numSubScrInWin;++i){scbrs[i] = new myScrollBars(pa, this);}}
	}//initThisWin	
	
	//final initialization stuff, after window made, but necessary to make sure window displays correctly
	public void finalInit(boolean thisIs3D, boolean viewCanChange, myPoint _ctr, myVector _baseFcs) {
		setFlags(is3DWin, thisIs3D);
		setFlags(canChgView, viewCanChange);
		sceneFcsVal = new myVector(_baseFcs);
		sceneCtrVal = new myPoint(_ctr);
		focusTar = new myVector(_baseFcs);		
	}
	
	protected void initTmpTrajStuff(boolean _trajIsFlat){
		tmpDrawnTraj= new myDrawnSmplTraj(pa,this,topOffY,trajFillClrCnst, trajStrkClrCnst, _trajIsFlat, !_trajIsFlat);
		curDrnTrajScrIDX = 0;
	}	
	//init fill and stroke colors and dims of rectangular area open and closed - only called from ctor
	private void initClrDims(int[] fc,  int[] sc, float[] rd, float[] rdClosed) {
		fillClr = new int[4];rtSideUIFillClr= new int[4]; rtSideUIStrkClr= new int[4]; strkClr = new int[4];	 
		rectDim = new float[4];	rectDimClosed = new float[4]; closeBox = new float[4]; uiClkCoords = new float[4];		
		for(int i =0;i<4;++i){
			fillClr[i] = fc[i];strkClr[i]=sc[i];
			rtSideUIFillClr[i] = fc[i];rtSideUIStrkClr[i]=sc[i];			
			rectDim[i]=rd[i];rectDimClosed[i]=rdClosed[i];
		}			
		
		float boxWidth = 1.1f*rectDim[0];
		UIRtSideRectBox = new float[] {rectDim[2]-boxWidth,0,boxWidth, rectDim[3]};		
		closedUIRtSideRecBox = new float[] {rectDim[2]-20,0,20,rectDim[3]};

		curVisScrDims = new float[] {closedUIRtSideRecBox[0],rectDim[3]};
	}//initClrDims	
	
	protected void setVisScreenWidth(float visScrWidth) {setVisScreenDims(visScrWidth,curVisScrDims[1]);}
	protected void setVisScreenHeight(float visScrHeight) {setVisScreenDims(curVisScrDims[0],visScrHeight);}
	//based on current visible screen width, set map and calc analysis display locations
	protected void setVisScreenDims(float visScrWidth, float visScrHeight) {
		curVisScrDims[0] = visScrWidth;
		curVisScrDims[1] = visScrHeight;
		setVisScreenDimsPriv();
	}//calcAndSetMapLoc
	
	//set right side data display fill/stroke colors
	public void setRtSideUIBoxClrs(int[] fc,  int[] sc) {
		for(int i =0;i<4;++i){rtSideUIFillClr[i] = fc[i];rtSideUIStrkClr[i]=sc[i];}				
	}		
	//initialize traj-specific stuff for this window
	protected void initTrajStructs(){
		drwnTrajMap = new TreeMap<Integer,TreeMap<String,ArrayList<myDrawnSmplTraj>>>();
		TreeMap<String,ArrayList<myDrawnSmplTraj>> tmpTrajMap;
		for(int scr =0;scr<numSubScrInWin; ++scr){
			tmpTrajMap = new TreeMap<String,ArrayList<myDrawnSmplTraj>>();
			for(int traj =0; traj<numTrajInSubScr[scr]; ++traj){
				tmpTrajMap.put(getTrajAraKeyStr(traj), new ArrayList<myDrawnSmplTraj>());			
			}	
			drwnTrajMap.put(scr, tmpTrajMap);
		}		
	}	
	//build UI clickable region
	protected void initUIClickCoords(float x1, float y1, float x2, float y2){uiClkCoords[0] = x1;uiClkCoords[1] = y1;uiClkCoords[2] = x2; uiClkCoords[3] = y2;}
	protected void initUIClickCoords(float[] cpy){	uiClkCoords[0] = cpy[0];uiClkCoords[1] = cpy[1];uiClkCoords[2] = cpy[2]; uiClkCoords[3] = cpy[3];}
	//public void initFlags(){dispFlags = new boolean[numDispFlags];for(int i =0; i<numDispFlags;++i){dispFlags[i]=false;}}		
	//base class flags init
	public void initFlags(){dispFlags = new int[1 + numDispFlags/32];for(int i =0; i<numDispFlags;++i){setFlags(i,false);}}		
	//child-class flag init
	protected void initPrivFlags(int numPrivFlags){privFlags = new int[1 + numPrivFlags/32]; for(int i = 0; i<numPrivFlags; ++i){setPrivFlags(i,false);}}
	//set up initial colors for sim specific flags for display
	protected void initPrivFlagColors(){
		privFlagColors = new int[truePrivFlagNames.length][4];
		for (int i = 0; i < privFlagColors.length; ++i) { privFlagColors[i] = new int[]{(int) pa.random(150),(int) pa.random(100),(int) pa.random(150), 255}; }			
	}
	
	//set up child class button rectangles
	protected void initUIBox(){		
		float [] menuUIClkCoords = pa.getUIRectVals(ID); 
		initUIClickCoords(menuUIClkCoords[0],menuUIClkCoords[3],menuUIClkCoords[2],menuUIClkCoords[3]);			
	}
	
	//calculate button length
	private static final float ltrLen = 5.0f;private static final int btnStep = 5;
	private float calcBtnLength(String tStr, String fStr){return btnStep * (int)(((PApplet.max(tStr.length(),fStr.length())+4) * ltrLen)/btnStep);}
	
	private void setBtnDims(int idx, float oldBtnLen, float btnLen) {privFlagBtns[idx]= new float[] {(float)(uiClkCoords[0])+oldBtnLen, (float) uiClkCoords[3], btnLen, yOff };}
	
	//set up child class button rectangles TODO
	//yDisp is displacement for button to be drawn
	protected void initPrivBtnRects(float yDisp, int numBtns){
		//pa.outStr2Scr("initPrivBtnRects in :"+ name + "st value for uiClkCoords[3]");
		float maxBtnLen = maxBtnWidthMult * pa.menuWidth, halfBtnLen = .5f*maxBtnLen;
		//pa.pr("maxBtnLen : " + maxBtnLen);
		privFlagBtns = new float[numBtns][];
		this.uiClkCoords[3] += yOff;
		float oldBtnLen = 0;
		boolean lastBtnHalfStLine = false, startNewLine = true;
		for(int i=0; i<numBtns; ++i){						//clickable button regions - as rect,so x,y,w,h - need to be in terms of sidebar menu 
			float btnLen = calcBtnLength(truePrivFlagNames[i].trim(),falsePrivFlagNames[i].trim());
			//either button of half length or full length.  if half length, might be changed to full length in next iteration.
			//pa.pr("initPrivBtnRects: i "+i+" len : " +btnLen+" cap 1: " + truePrivFlagNames[i].trim()+"|"+falsePrivFlagNames[i].trim());
			if(btnLen > halfBtnLen){//this button is bigger than halfsize - it needs to be made full size, and if last button was half size and start of line, make it full size as well
				btnLen = maxBtnLen;
				if(lastBtnHalfStLine){//make last button full size, and make button this button on another line
					privFlagBtns[i-1][2] = maxBtnLen;
					this.uiClkCoords[3] += yOff;
				}
				setBtnDims(i, 0, btnLen);
				//privFlagBtns[i]= new float[] {(float)(uiClkCoords[0]-xOff), (float) uiClkCoords[3], btnLen, yOff };				
				this.uiClkCoords[3] += yOff;
				startNewLine = true;
				lastBtnHalfStLine = false;
			} else {//button len should be half width unless this button started a new line
				btnLen = halfBtnLen;
				if(startNewLine){//button is starting new line
					lastBtnHalfStLine = true;
					setBtnDims(i, 0, btnLen);
					//privFlagBtns[i]= new float[] {(float)(uiClkCoords[0]-xOff), (float) uiClkCoords[3], btnLen, yOff };
					startNewLine = false;
				} else {//should only get here if 2nd of two <1/2 width buttons in a row
					lastBtnHalfStLine = false;
					setBtnDims(i, oldBtnLen, btnLen);
					//privFlagBtns[i]= new float[] {(float)(uiClkCoords[0]-xOff)+oldBtnLen, (float) uiClkCoords[3], btnLen, yOff };
					this.uiClkCoords[3] += yOff;
					startNewLine = true;					
				}
			}			
			oldBtnLen = btnLen;
		}
		if(lastBtnHalfStLine){//set last button full length if starting new line
			privFlagBtns[numBtns-1][2] = maxBtnLen;			
		}
		this.uiClkCoords[3] += yOff;
		initPrivFlagColors();
	}//initPrivBtnRects
	//find index in flag name arrays of passed boolean IDX
	protected int getFlagAraIdxOfBool(int idx) {
		for(int i=0;i<privModFlgIdxs.length;++i) {if(idx == privModFlgIdxs[i]) {return i;}	}
		//not found
		return -1;
	}
	
	//set baseclass flags  //setFlags(showIDX, 
	public void setFlags(int idx, boolean val){
		int flIDX = idx/32, mask = 1<<(idx%32);
		dispFlags[flIDX] = (val ?  dispFlags[flIDX] | mask : dispFlags[flIDX] & ~mask);
		switch(idx){
			case showIDX 			: {	
				setClosedBox();
				if(!val){//not showing window
						closeMe();//specific instancing window implementation stuff to do when hidden/transitioning to another window (i.e. suspend stuff running outside draw loop, or release memory of unnecessary stuff)
					} else {
						showMe();//specific instance window functionality to do when window is shown
					}
				break;}	
			case is3DWin 			: {	break;}	
			case closeable 			: {	break;}	
			case hasScrollBars 		: {	break;}	
			case canDrawTraj 		: {	break;}	
			case drawingTraj 		: {	break;}	
			case editingTraj 		: {	break;}	
			case showTrajEditCrc 	: {	break;}	
			case smoothTraj 		: {	break;}	
			case trajDecays 		: {	break;}	
			case trajPointsAreFlat 	: {	break;}	
			case procMouseMove 		: {	break;}	
			case mouseSnapMove		: {	break;}	
			case uiObjMod			: {	break;}			
			case useRndBtnClrs		: { break;}
			case useCustCam			: { break;}
			case drawMseEdge		: { break;}
			case clearPrivBtns		: { break;}
			case drawRightSideMenu  : { break;}	//can drawn right side menu
			case showRightSideMenu  : { 		//modify the dimensions of the visible window based on whether the side bar menu is shown
				if(getFlags(drawRightSideMenu)) {
					float visWidth = (val ?  UIRtSideRectBox[0] : closedUIRtSideRecBox[0]);		//to match whether the side bar menu is open or closed
					setVisScreenWidth(visWidth);
				}
				break;}		
		}				
	}//setFlags

	//set the right side menu state for this window - if it is actually present, show it
	public void setRtSideInfoWinSt(boolean visible) {if(getFlags(drawRightSideMenu)) {setFlags(showRightSideMenu,visible);}}		
	//get baseclass flag
	public boolean getFlags(int idx){int bitLoc = 1<<(idx%32);return (dispFlags[idx/32] & bitLoc) == bitLoc;}	
	//check list of flags
	public boolean getAllFlags(int [] idxs){int bitLoc; for(int idx =0;idx<idxs.length;++idx){bitLoc = 1<<(idx%32);if ((dispFlags[idx/32] & bitLoc) != bitLoc){return false;}} return true;}
	public boolean getAnyFlags(int [] idxs){int bitLoc; for(int idx =0;idx<idxs.length;++idx){bitLoc = 1<<(idx%32);if ((dispFlags[idx/32] & bitLoc) == bitLoc){return true;}} return false;}
	
	//set/get child class flags
	public abstract void setPrivFlags(int idx, boolean val);
	public boolean getPrivFlags(int idx){int bitLoc = 1<<(idx%32);return (privFlags[idx/32] & bitLoc) == bitLoc;}	
	public boolean getAllPrivFlags(int [] idxs){int bitLoc; for(int idx =0;idx<idxs.length;++idx){bitLoc = 1<<(idx%32);if ((privFlags[idx/32] & bitLoc) != bitLoc){return false;}} return true;}
	public boolean getAnyPrivFlags(int [] idxs){int bitLoc; for(int idx =0;idx<idxs.length;++idx){bitLoc = 1<<(idx%32);if ((privFlags[idx/32] & bitLoc) == bitLoc){return true;}} return false;}
	//set a list of indexes in private flags array to be a specific value
	public void setAllPrivFlags(int[] idxs, boolean val) { for(int idx =0;idx<idxs.length;++idx) {setPrivFlags(idxs[idx],val);}}

	//for adding/deleting a screen programatically  TODO
	//rebuild arrays of start locs whenever trajectory maps/arrays have changed - passed key is value modded in drwnTrajMap, 
	//modVal is if this is a deleted screen's map(0), a new map (new screen) at this location (1), or a modified map (added or deleted trajectory) (2)
	protected void rbldTrnsprtAras(int modScrKey, int modVal){
		if(modVal == -1){return;}//denotes no mod taken place
		int tmpNumSubScrInWin = drwnTrajMap.size();
		int [] tmpNumTrajInSubScr = new int[tmpNumSubScrInWin];
		float [] tmpVsblStLoc = new float[tmpNumSubScrInWin];
		int [] tmpSeqVisStTime = new int[tmpNumSubScrInWin];
		if(modVal == 0){			//deleted a screen's map
			if(tmpNumSubScrInWin != (numSubScrInWin -1)){pa.outStr2Scr("Error in rbldTrnsprtAras : screen traj map not removed at idx : " + modScrKey); return;}
			for(int i =0; i< numSubScrInWin; ++i){					
			}			
			
		} else if (modVal == 1){	//added a new screen, with a new map			
		} else if (modVal == 2){	//modified an existing map (new or removed traj ara)			
		}
		numSubScrInWin = tmpNumSubScrInWin;
		numTrajInSubScr = tmpNumTrajInSubScr;
	}//rbldTrnsprtAras
	
	public String[] getSaveFileDirName() {
		String[] vals = getSaveFileDirNamesPriv();
		String[] res = new String[] {
			ssPathBase + vals[0] + File.separatorChar, vals[1]	
		};
		return res;
	}
	
	//for adding/deleting a screen programatically (loading a song) TODO
	//add or delete a new map of treemaps (if trajAraKey == "" or null), or a new map of traj arrays to existing key map
	protected void modTrajStructs(int scrKey, String trajAraKey, boolean del){
		int modMthd = -1;
		if(del){//delete a screen's worth of traj arrays, or a single traj array from a screen 
			if((trajAraKey == null) || (trajAraKey == "") ){		//delete screen map				
				TreeMap<String,ArrayList<myDrawnSmplTraj>> tmpTrajMap = drwnTrajMap.remove(scrKey);
				if(null != tmpTrajMap){			pa.outStr2Scr("Screen trajectory map removed for scr : " + scrKey);				modMthd = 0;}
				else {							pa.outStr2Scr("Error : Screen trajectory map not found for scr : " + scrKey); 	modMthd = -1; }
			} else {												//delete a submap within a screen
				modMthd = 2;					//modifying existing map at this location
				TreeMap<String,ArrayList<myDrawnSmplTraj>> tmpTrajMap = drwnTrajMap.get(scrKey);
				if(null == tmpTrajMap){pa.outStr2Scr("Error : Screen trajectory map not found for scr : " + scrKey + " when trying to remove arraylist : "+trajAraKey); modMthd = -1;}
				else { 
					ArrayList<myDrawnSmplTraj> tmpTrajAra = drwnTrajMap.get(scrKey).remove(trajAraKey);modMthd = 2;
					if(null == tmpTrajAra){pa.outStr2Scr("Error : attempting to remove a trajectory array from a screen but trajAra not found. scr : " + scrKey + " | trajAraKey : "+trajAraKey);modMthd = -1; }
				}
			}			 
		} else {													//add
			TreeMap<String,ArrayList<myDrawnSmplTraj>> tmpTrajMap = drwnTrajMap.get(scrKey);
			if((trajAraKey == null) || (trajAraKey == "") ){		//add map of maps - added a new screen				
				if(null != tmpTrajMap){pa.outStr2Scr("Error : attempting to add a new drwnTrajMap where one exists. scr : " + scrKey);modMthd = -1; }
				else {tmpTrajMap = new TreeMap<String,ArrayList<myDrawnSmplTraj>>();	drwnTrajMap.put(scrKey, tmpTrajMap);modMthd = 1;}
			} else {												//add new map of trajs to existing screen's map
				ArrayList<myDrawnSmplTraj> tmpTrajAra = drwnTrajMap.get(scrKey).get(trajAraKey);	
				if(null == tmpTrajMap){pa.outStr2Scr("Error : attempting to add a new trajectory array to a screen that doesn't exist. scr : " + scrKey + " | trajAraKey : "+trajAraKey); modMthd = -1; }
				else if(null != tmpTrajAra){pa.outStr2Scr("Error : attempting to add a new trajectory array to a screen where one already exists. scr : " + scrKey + " | trajAraKey : "+trajAraKey);modMthd = -1; }
				else {	tmpTrajAra = new ArrayList<myDrawnSmplTraj>();			tmpTrajMap.put(trajAraKey, tmpTrajAra);	drwnTrajMap.put(scrKey, tmpTrajMap);modMthd = 2;}
			}			
		}//if del else add
		//rebuild arrays of start loc
		rbldTrnsprtAras(scrKey, modMthd);
	}
	
	//this will set the height of the rectangle enclosing this window - this will be called when a window pushes up or pulls down this window
	//this resizes any drawn trajectories in this window, and calls the instance class's code for resizing
	public void setRectDimsY(float height){
		float oldVal = getFlags(showIDX) ? rectDim[3] : rectDimClosed[3];
		rectDim[3] = height;
		rectDimClosed[3] = height;
		float scale  = height/oldVal;			//scale of modification - rescale the size and location of all components of this window by this
		//resize drawn all trajectories
		TreeMap<String,ArrayList<myDrawnSmplTraj>> tmpTreeMap = drwnTrajMap.get(this.curDrnTrajScrIDX);
		if((tmpTreeMap != null) && (tmpTreeMap.size() != 0)) {
			for(int i =0; i<tmpTreeMap.size(); ++i){
				ArrayList<myDrawnSmplTraj> tmpAra = tmpTreeMap.get(getTrajAraKeyStr(i));			
				if(null!=tmpAra){	for(int j =0; j<tmpAra.size();++j){		tmpAra.get(j).reCalcCntlPoints(scale);	}	}
			}	
		}
		if(getFlags(hasScrollBars)){for(int i =0; i<scbrs.length;++i){scbrs[i].setSize();}}
		resizeMe(scale);
	}
//		//resize and relocate UI objects in this window for resizing window
//		public void resizeUIRegion(float scaleY, int numGUIObjs){
//			//re-size where UI objects should be drawn - vertical scale only currently
//			double [] curUIVals = new double[this.guiStVals.length];
//			for(int i=0;i<curUIVals.length;++i){	curUIVals[i] = guiObjs[i].getVal();	}
//			
//			uiClkCoords[1]=calcDBLOffsetScale(uiClkCoords[1],scaleY,topOffY);
//			uiClkCoords[3]=calcDBLOffsetScale(uiClkCoords[3],scaleY,topOffY);
//			//initUIClickCoords(uiClkCoords[0],uiClkCoords[1],uiClkCoords[2],uiClkCoords[3]);
//			if(0!=numGUIObjs){
//				buildGUIObjs(guiObjNames,curUIVals,guiMinMaxModVals,guiBoolVals,new double[]{xOff,yOff});
//			}
//		}
	
	//build myGUIObj objects for interaction - call from setupMenuClkRegions of window, 
	//uiClkCoords needs to be derived before this is called by child class - maxY val(for vertical stack) or maxX val(for horizontal stack) will be derived here
	protected void buildGUIObjs(String[] guiObjNames, double[] guiStVals, double[][] guiMinMaxModVals, boolean[][] guiBoolVals, double[] off){
		//myGUIObj tmp; 
//			if(getFlags(uiObjsAreVert]){		//vertical stack of UI components - clickable region x is unchanged, y changes with # of objects
		float stClkY = uiClkCoords[1];
		for(int i =0; i< guiObjs.length; ++i){
			guiObjs[i] = buildGUIObj(i,guiObjNames[i],guiStVals[i], guiMinMaxModVals[i], guiBoolVals[i], new double[]{uiClkCoords[0], stClkY, uiClkCoords[2], stClkY+yOff},off);
			stClkY += yOff;
		}
		uiClkCoords[3] = stClkY;	
//			} else {			//horizontal row of UI components - clickable region y is unchanged, x changes with # of objects
//				double stClkX = uiClkCoords[0];
//				double UICompWidth;
//				for(int i =0; i< guiObjs.length; ++i){
//					UICompWidth = (uiWidthMult + (guiBoolVals[i][1] ? 1 : 0)) * guiObjNames[i].length();
//					guiObjs[i] = buildGUIObj(i,guiObjNames[i],guiStVals[i], guiMinMaxModVals[i], guiBoolVals[i], new double[]{stClkX, uiClkCoords[1], stClkX+UICompWidth , uiClkCoords[3]},off);
//					stClkX += UICompWidth;
//				}
//				uiClkCoords[2] = stClkX;	
//			}
	}//
	
	protected myGUIObj buildGUIObj(int i, String guiObjName, double guiStVal, double[] guiMinMaxModVals, boolean[] guiBoolVals, double[] xyDims, double[] off){
//			myGUIObj tmp;
//			tmp = new myGUIObj(pa, this,i, guiObjName, xyDims[0], xyDims[1], xyDims[2], xyDims[3], guiMinMaxModVals, guiStVal, guiBoolVals, off);		
		return new myGUIObj(pa, this,i, guiObjName, xyDims[0], xyDims[1], xyDims[2], xyDims[3], guiMinMaxModVals, guiStVal, guiBoolVals, off);
	}	
	
	//this returns a formatted string holding the UI data
	protected String getStrFromUIObj(int idx){
		StringBuilder sb = new StringBuilder(400);
		sb.append("ui_idx: ");
		sb.append(idx);
		sb.append(" |name: ");
		sb.append(guiObjs[idx].name);
		sb.append(" |value: ");
		sb.append(guiObjs[idx].getVal());
		sb.append(" |flags: ");
		for(int i =0;i<guiObjs[idx].numFlags; ++i){
			sb.append(" ");
			sb.append((guiObjs[idx].getFlags(i) ? "true" : "false"));
		}
		return sb.toString().trim();		
		
	}//getStrFromUIObj
		
	//this sets the value of a gui object from the data held in a string
	protected void setValFromFileStr(String str){
		String[] toks = str.trim().split("\\|");
		//window has no data values to load
		if(toks.length==0){return;}
		int uiIdx = Integer.parseInt(toks[0].split("\\s")[1].trim());
		//String name = toks[3];
		double uiVal = Double.parseDouble(toks[2].split("\\s")[1].trim());	
		guiObjs[uiIdx].setVal(uiVal);
		for(int i =0;i<guiObjs[uiIdx].numFlags; ++i){
			guiObjs[uiIdx].setFlags(i, Boolean.parseBoolean(toks[3].split("\\s")[i].trim()));
		}	
		setUIWinVals(uiIdx);//update window's values with UI construct's values
	}//setValFromFileStr

	public void loadFromFile(File file){
		if (file == null) {
		    pa.outStr2Scr("Load was cancelled.");
		    return;
		} 
		String[] res = pa.loadStrings(file.getAbsolutePath());
		int[] stIdx = {0};//start index for a particular window - make an array so it can be passed by ref and changed by windows
		hndlFileLoad(file, res,stIdx);
	}//loadFromFile
	
	public void saveToFile(File file){
		if (file == null) {
		    pa.outStr2Scr("Save was cancelled.");
		    return;
		} 
		ArrayList<String> res = new ArrayList<String>();

		res.addAll(hndlFileSave(file));	

		pa.saveStrings(file.getAbsolutePath(), res.toArray(new String[0]));  
	}//saveToFile	
	
	//manage loading pre-saved UI component values, if useful for this window's load/save (if so call from child window's implementation
	protected void hndlFileLoad_GUI(String[] vals, int[] stIdx) {
		++stIdx[0];
		//set values for ui sliders
		while(!vals[stIdx[0]].contains(name + "_custUIComps")){
			if(vals[stIdx[0]].trim() != ""){	setValFromFileStr(vals[stIdx[0]]);	}
			++stIdx[0];
		}
		++stIdx[0];		
		
	}//hndlFileLoad_GUI
	//manage saving this window's UI component values.  if needed call from child window's implementation
	protected ArrayList<String> hndlFileSave_GUI(){
		ArrayList<String> res = new ArrayList<String>();
		res.add(name);
		for(int i=0;i<guiObjs.length;++i){	res.add(getStrFromUIObj(i));}		
		//bound for custom components
		res.add(name + "_custUIComps");
		//add blank space
		res.add("");
		return res;
	}//
	

//	//take loaded params and process - stIdx will be idx of this window's name - move forward 1 to start on objects
//	protected void hndlFileLoad(String[] vals, int[] stIdx){
//		++stIdx[0];
//		//set values for ui sliders
//		while(!vals[stIdx[0]].contains(name + "_custUIComps")){
//			if(vals[stIdx[0]].trim() != ""){	setValFromFileStr(vals[stIdx[0]]);	}
//			++stIdx[0];
//		}
//		++stIdx[0];		
//		//handle window-specific UI components, if any
//		this.hndlFileLoadIndiv(vals, stIdx);
//	}//hndlFileLoad
//	
//	//accumulate array of params to save
//	protected ArrayList<String> hndlFileSave(){
//		ArrayList<String> res = new ArrayList<String>();
//		res.add(name);
//		for(int i=0;i<guiObjs.length;++i){	res.add(getStrFromUIObj(i));}		
//		//bound for custom components
//		res.add(name + "_custUIComps");
//		//call indiv handler
//		res.addAll(hndlFileSaveIndiv());
//		//add blank space
//		res.add("");
//		return res;
//	}//
	
	public float calcOffsetScale(double val, float sc, float off){float res =(float)val - off; res *=sc; return res+=off;}
	public double calcDBLOffsetScale(double val, float sc, double off){double res = val - off; res *=sc; return res+=off;}
	//returns passed current passed dimension from either rectDim or rectDimClosed
	public float getRectDim(int idx){return ( getFlags(showIDX) ? rectDim[idx] : rectDimClosed[idx]);	}

	public void setClosedBox(){
		if( getFlags(showIDX)){	closeBox[0] = rectDim[0]+rectDim[2]-clkBxDim;closeBox[1] = rectDim[1];	closeBox[2] = clkBxDim;	closeBox[3] = clkBxDim;} 
		else {					closeBox[0] = rectDimClosed[0]+rectDimClosed[2]-clkBxDim;closeBox[1] = rectDimClosed[1];	closeBox[2] = clkBxDim;	closeBox[3] = clkBxDim;}
	}	
	
	//set up initial trajectories - 2d array, 1 per UI Page, 1 per modifiable construct within page.
	public void initDrwnTrajs(){
		numSubScrInWin = 2;						//# of subscreens in a window.  will generally be 1, but with sequencer will have at least 2 (piano roll and score view)
		int numTrajPerScr = 10;
		trajNameAra = new String[numTrajPerScr];
		numTrajInSubScr = new int[]{numTrajPerScr,numTrajPerScr};	
		for(int i =0; i<numTrajPerScr; ++i){
			trajNameAra[i] = "traj_"+(i+1);
		}
		initTrajStructs();		
		//setupGUIObjsAras();					//rebuild UI object stuff
		initDrwnTrajIndiv();				
	}
	
	//displays point with a name
	protected void showKeyPt(myPoint a, String s, float rad){	pa.show(a,rad, s, new myVector(10,-5,0), my_procApplet.gui_Cyan, getFlags(trajPointsAreFlat));	}	
	//draw a series of strings in a column
	protected void dispMenuTxtLat(String txt, int[] clrAra, boolean showSphere){
		pa.setFill(clrAra, 255); 
		pa.translate(xOff*.5f,yOff*.5f);
		if(showSphere){pa.setStroke(clrAra, 255);		pa.sphere(5);	} 
		else {	pa.noStroke();		}
		pa.translate(-xOff*.5f,yOff*.5f);
		pa.text(""+txt,xOff,-yOff*.25f);	
	}
	protected void dispBoolStFlag(String txt, int[] clrAra, boolean state, float stMult){
		if(state){
			pa.setFill(clrAra, 255); 
			pa.setStroke(clrAra, 255);
		} else {
			pa.setColorValFill(pa.gui_DarkGray); 
			pa.noStroke();	
		}
		pa.sphere(5);
		//pa.text(""+txt,-xOff,yOff*.8f);	
		pa.text(""+txt,stMult*txt.length(),yOff*.8f);	
	}
	
	//draw a series of strings in a row
	protected void dispBttnAtLoc(String txt, float[] loc, int[] clrAra){
		pa.setFill(clrAra);
		pa.setColorValStroke(my_procApplet.gui_Black);
		pa.rect(loc);		
		pa.setColorValFill(my_procApplet.gui_Black);
		//pa.translate(-xOff*.5f,-yOff*.5f);
		pa.text(""+txt,loc[0] + (txt.length() * .3f),loc[1]+loc[3]*.75f);
		//pa.translate(width, 0);
	}
	
	//whether or not to draw the mouse reticle/rgb(xyz) projection/edge to eye
	public boolean chkDrawMseRet(){		return getFlags(drawMseEdge);	}
	
	protected void drawTraj(float animTimeMod){
		pa.pushMatrix();pa.pushStyle();	
		if(null != tmpDrawnTraj){tmpDrawnTraj.drawMe(animTimeMod);}
		TreeMap<String,ArrayList<myDrawnSmplTraj>> tmpTreeMap = drwnTrajMap.get(this.curDrnTrajScrIDX);
		if((tmpTreeMap != null) && (tmpTreeMap.size() != 0)) {
			for(int i =0; i<tmpTreeMap.size(); ++i){
				ArrayList<myDrawnSmplTraj> tmpAra = tmpTreeMap.get(getTrajAraKeyStr(i));			
				if(null!=tmpAra){	for(int j =0; j<tmpAra.size();++j){tmpAra.get(j).drawMe(animTimeMod);}}
			}	
		}
		pa.popStyle();pa.popMatrix();		
	}
	
	//draw ui objects
	public void drawGUIObjs(){	
		pa.pushMatrix();pa.pushStyle();	
		for(int i =0; i<guiObjs.length; ++i){guiObjs[i].draw();}
		pa.popStyle();pa.popMatrix();
	}
	
	//draw all boolean-based buttons for this window
	public void drawClickableBooleans() {	
		pa.pushMatrix();pa.pushStyle();	
		pa.setColorValFill(pa.gui_Black);
		if(getFlags(useRndBtnClrs)){
			for(int i =0; i<privModFlgIdxs.length; ++i){//prlFlagRects dispBttnAtLoc(String txt, float[] loc, int[] clrAra)
				if(getPrivFlags(privModFlgIdxs[i]) ){									dispBttnAtLoc(truePrivFlagNames[i],privFlagBtns[i],privFlagColors[i]);			}
				else {	if(truePrivFlagNames[i].equals(falsePrivFlagNames[i])) {	dispBttnAtLoc(truePrivFlagNames[i],privFlagBtns[i],new int[]{180,180,180, 255});}	
						else {														dispBttnAtLoc(falsePrivFlagNames[i],privFlagBtns[i],new int[]{0,255-privFlagColors[i][1],255-privFlagColors[i][2], 255});}		
				}
			}		
		} else {
			for(int i =0; i<privModFlgIdxs.length; ++i){//prlFlagRects dispBttnAtLoc(String txt, float[] loc, int[] clrAra)
				if(getPrivFlags(privModFlgIdxs[i]) ){								dispBttnAtLoc(truePrivFlagNames[i],privFlagBtns[i],trueBtnClr);			}
				else {																dispBttnAtLoc(falsePrivFlagNames[i],privFlagBtns[i],falseBtnClr);	}
			}	
		}
		pa.popStyle();pa.popMatrix();
	}//drawClickableBooleans
	
	//draw any custom menu objects for sidebar menu
	public abstract void drawCustMenuObjs();
	
	public abstract void initAllPrivBtns();
	
	//////////////////////
	//camera stuff
	
	//resets camera view and focus target
	public void setInitCamView(){
		rx = camInitRx;
		ry = camInitRy;
		dz = camInitialDist;	
		resetViewFocus();
	}//setCamView()	

	public void setCamera(float[] camVals){
		if(getFlags(useCustCam)){setCameraIndiv (camVals);}//individual window camera handling
		else {
			pa.camera(camVals[0],camVals[1],camVals[2],camVals[3],camVals[4],camVals[5],camVals[6],camVals[7],camVals[8]);      
			//if(this.flags[this.debugMode]){outStr2Scr("rx :  " + rx + " ry : " + ry + " dz : " + dz);}
			// puts origin of all drawn objects at screen center and moves forward/away by dz
			pa.translate(camVals[0],camVals[1],(float)dz); 
		    setCamOrient();	
		}
	}//setCamera

	//used to handle camera location/motion
	public void setCamOrient(){pa.rotateX(rx);pa.rotateY(ry); pa.rotateX(HALF_PI);		}//sets the rx, ry, pi/2 orientation of the camera eye	
	//used to draw text on screen without changing mode - reverses camera orientation setting
	public void unSetCamOrient(){pa.rotateX(-HALF_PI); pa.rotateY(-ry);   pa.rotateX(-rx); }//reverses the rx,ry,pi/2 orientation of the camera eye - paints on screen and is unaffected by camera movement
	//return display string for camera location
	public String getCamDisp() {return " camera rx :  " + rx + " ry : " + ry + " dz : " + dz ; }
	
	//initial draw stuff
	public void drawSetupWin(float[] camVals) {
		setCamera(camVals);
		pa.translate(focusTar.x,focusTar.y,focusTar.z);
	}
	
	//recenter view on original focus target
	public void resetViewFocus() {focusTar.set(sceneFcsVal);}
	
	//draw box to hide window
	protected void drawMouseBox(){
		if( getFlags(showIDX)){
		    pa.setColorValFill(my_procApplet.gui_LightGreen );
			pa.rect(closeBox);
			pa.setFill(strkClr);
			pa.text("Close" , closeBox[0]-35, closeBox[1]+10);
		} else {
		    pa.setColorValFill(my_procApplet.gui_DarkRed);
			pa.rect(closeBox);
			pa.setFill(strkClr);
			pa.text("Open", closeBox[0]-35, closeBox[1]+10);			
		}
	}
	public void drawSmall(){
		pa.pushMatrix();				pa.pushStyle();	
		//pa.outStr2Scr("Hitting hint code draw small");
		pa.hint(PConstants.DISABLE_DEPTH_TEST);
		pa.noLights();		
		pa.setStroke(strkClr);
		pa.setFill(fillClr);
		//main window drawing
		pa.rect(rectDimClosed);		
		pa.setFill(strkClr);
		if(winText.trim() != ""){
			pa.text(winText.split(" ")[0], rectDimClosed[0]+10, rectDimClosed[1]+25);
		}		
		//close box drawing
		if(getFlags(closeable)){drawMouseBox();}
		pa.hint(PConstants.ENABLE_DEPTH_TEST);
		pa.popStyle();pa.popMatrix();		
	}
	
	public void drawHeader(float modAmtMillis){
		if(!getFlags(showIDX)){return;}
		pa.pushMatrix();				pa.pushStyle();			
		//pa.outStr2Scr("Hitting hint code drawHeader");
		pa.hint(PConstants.DISABLE_DEPTH_TEST);
		pa.noLights();		
		pa.setStroke(strkClr);
		pa.setFill(strkClr);
		if(winText.trim() != ""){	pa.ml_text(winText,  rectDim[0]+10,  rectDim[1]+10);}
		if(getFlags(canDrawTraj)){	drawNotifications();	}				//if this window accepts a drawn trajectory, then allow it to be displayed
		if(getFlags(closeable)){drawMouseBox();}
		if(getFlags(hasScrollBars)){scbrs[curDrnTrajScrIDX].drawMe();}
		//draw rightSideMenu stuff, if this window supports it
		if(getFlags(drawRightSideMenu)) {drawRtSideInfoBar(modAmtMillis);	}
		pa.lights();	
		pa.hint(PConstants.ENABLE_DEPTH_TEST);
		pa.popStyle();pa.popMatrix();	
		//last thing per draw - clear btns that have been set to clear after 1 frame of display
		if (getFlags(clearPrivBtns)) {clearAllPrivBtns();setFlags(clearPrivBtns,false);}
//		//if buttons have been set to clear, clear them next draw - put this in mouse release?
//		if (privBtnsToClear.size() > 0){setFlags(clearPrivBtns, true);	}		
	}//drawHeader
	
	//draw right side "menu" used to display simualtion/calculation variables and results
	private void drawRtSideInfoBar(float modAmtMillis) {
		pa.pushMatrix();pa.pushStyle();
		//move to upper right corner of sidebar menu - cannot draw over leftside menu, use drawCustMenuObjs() instead to put UI objects there
		//this side window is for information display
		pa.translate(rectDim[0],0,0);			
		//draw onscreen stuff for main window
		drawOnScreenStuffPriv(modAmtMillis);
		//draw right side info display if relelvant
		if(getFlags(drawRightSideMenu)) {
			pa.setFill(rtSideUIFillClr);//transparent black
			if(getFlags(showRightSideMenu)) {				
				pa.rect(UIRtSideRectBox);
				//move to manage internal text display in owning window
				pa.translate(UIRtSideRectBox[0]+5,UIRtSideRectBox[1]+yOff-4,0);
				pa.setFill(new int[] {255,255,255,255});	
				 //instancing class implements this function
				drawRightSideInfoBarPriv(modAmtMillis); 
			} else {
				//shows narrow rectangular reminder that window is there								 
				pa.rect(closedUIRtSideRecBox);
			}
		}
		pa.popStyle();pa.popMatrix();			
	}//drawRtSideInfoBar
	
	public void draw3D(float modAmtMillis){
		if(!getFlags(showIDX)){return;}
		//stAnimTime = pa.millis();
		float animTimeMod = (modAmtMillis/1000.0f);//in seconds
		//lastAnimTime = pa.millis();
		pa.pushMatrix();				pa.pushStyle();			
		pa.setFill(fillClr);
		pa.setStroke(strkClr);
		//if(getFlags(closeable)){drawMouseBox();}
		drawMe(animTimeMod);			//call instance class's draw
		if(getFlags(canDrawTraj)){
			pa.pushMatrix();				pa.pushStyle();	
			drawTraj3D(animTimeMod, myPoint._add(sceneCtrVal,focusTar));			
			pa.popStyle();pa.popMatrix();
			if(getFlags(showTrajEditCrc)){drawClkCircle();}
		}				//if this window accepts a drawn trajectory, then allow it to be displayed
		pa.popStyle();pa.popMatrix();		
	}//draw3D
	
	public void drawTraj3D(float animTimeMod,myPoint trans){
		pa.outStr2Scr("myDispWindow.drawTraj3D() : I should be overridden in 3d instancing class", true);
//			pa.pushMatrix();pa.pushStyle();	
//			if(null != tmpDrawnTraj){tmpDrawnTraj.drawMe(animTimeMod);}
//			TreeMap<String,ArrayList<myDrawnNoteTraj>> tmpTreeMap = drwnTrajMap.get(this.curDrnTrajScrIDX);
//			if((tmpTreeMap != null) && (tmpTreeMap.size() != 0)) {
//				for(int i =0; i<tmpTreeMap.size(); ++i){
//					ArrayList<myDrawnNoteTraj> tmpAra = tmpTreeMap.get(getTrajAraKeyStr(i));			
//					if(null!=tmpAra){	for(int j =0; j<tmpAra.size();++j){tmpAra.get(j).drawMe(animTimeMod);}}
//				}	
//			}
//			pa.popStyle();pa.popMatrix();		
	}//drawTraj3D
	
	public void draw2D(float modAmtMillis){
		if(!getFlags(showIDX)){drawSmall();return;}
		//stAnimTime = pa.millis();
		float animTimeMod = (modAmtMillis/1000.0f);
		//lastAnimTime = pa.millis();
		pa.pushMatrix();				pa.pushStyle();	
		//pa.outStr2Scr("Hitting hint code draw2D");
		pa.hint(PConstants.DISABLE_DEPTH_TEST);
		pa.setStroke(strkClr);
		pa.setFill(fillClr);
		//main window drawing
		pa.rect(rectDim);
		//close box drawing
		drawMe(animTimeMod);			//call instance class's draw
		if(getFlags(canDrawTraj)){
			drawTraj(animTimeMod);			
			if(getFlags(showTrajEditCrc)){drawClkCircle();}
		}				//if this window accepts a drawn trajectory, then allow it to be displayed
		pa.hint(PConstants.ENABLE_DEPTH_TEST);
		pa.popStyle();pa.popMatrix();
	}
	
	protected void drawNotifications(){		
		//debug stuff
		pa.pushMatrix();				pa.pushStyle();
		pa.translate(rectDim[0]+20,rectDim[1]+rectDim[3]-70);
		dispMenuTxtLat("Drawing curve", pa.getClr((getFlags(drawingTraj) ? my_procApplet.gui_Green : my_procApplet.gui_Red)), true);
		//pa.show(new myPoint(0,0,0),4, "Drawing curve",new myVector(10,15,0),(getFlags(this.drawingTraj) ? pa.gui_Green : pa.gui_Red));
		//pa.translate(0,-30);
		dispMenuTxtLat("Editing curve", pa.getClr((getFlags(editingTraj) ? my_procApplet.gui_Green : my_procApplet.gui_Red)), true);
		//pa.show(new myPoint(0,0,0),4, "Editing curve",new myVector(10,15,0),(getFlags(this.editingTraj) ? pa.gui_Green : pa.gui_Red));
		pa.popStyle();pa.popMatrix();		
	}

	protected void drawClkCircle(){
		pa.pushMatrix();				pa.pushStyle();
		boolean doneDrawing = true;
		for(int i =0; i<editCrcFillClrs.length;++i){
			if(editCrcCurRads[i] <= 0){continue;}
			pa.setColorValFill(editCrcFillClrs[i]);
			pa.noStroke();
			pa.circle(editCrcCtrs[i],editCrcCurRads[i]);
			editCrcCurRads[i] -= editCrcMods[i];
			doneDrawing = false;
		}
		if(doneDrawing){setFlags(showTrajEditCrc, false);}
		pa.popStyle();pa.popMatrix();		
	}
	
	public void simulate(float modAmtMillis){
		boolean simDone = simMe(modAmtMillis);
		if(simDone) {endSim();}
	}//
	
	//if ending simulation, call this function
	private void endSim() {	pa.setSimIsRunning(false);}//endSim
	
	//call after single draw - will clear window-based priv buttons that are momentary
	protected void clearAllPrivBtns() {
		if(privBtnsToClear.size() == 0) {return;}
		//only clear button if button is currently set to true, otherwise concurrent modification error
		for (Integer idx : privBtnsToClear) {this.setPrivFlags(idx, false);}
		privBtnsToClear.clear();
	}//clearPrivBtns()
	//add a button to clear after next draw
	protected void addPrivBtnToClear(int idx) {
		privBtnsToClear.add(idx);
	}
	
	protected boolean handleTrajClick(boolean keysToDrawClicked, myPoint mse){
		boolean mod = false;
		if(keysToDrawClicked){					//drawing curve with click+alt - drawing on canvas
			//pa.outStr2Scr("Current trajectory key IDX " + curTrajAraIDX);
			startBuildDrawObj();	
			mod = true;
			//
		} else {
		//	pa.outStr2Scr("Current trajectory key IDX edit " + curTrajAraIDX);
			this.tmpDrawnTraj = findTraj(mse);							//find closest trajectory to the mouse's click location
			
			if ((null != this.tmpDrawnTraj)  && (null != this.tmpDrawnTraj.drawnTraj)) {					//alt key not pressed means we're possibly editing a curve, if it exists and if we click within "sight" of it, or moving endpoints
				//pa.outStr2Scr("Current trajectory ID " + tmpDrawnTraj.ID);
				mod = this.tmpDrawnTraj.startEditObj(mse);
			}
		}
		return mod;
	}//
	
	public myDrawnSmplTraj findTraj(myPoint mse){
		TreeMap<String,ArrayList<myDrawnSmplTraj>> tmpTreeMap = drwnTrajMap.get(this.curDrnTrajScrIDX);
		if((tmpTreeMap != null) && (tmpTreeMap.size() != 0)) {
			for(int i =0; i<tmpTreeMap.size(); ++i){
				ArrayList<myDrawnSmplTraj> tmpAra = tmpTreeMap.get(getTrajAraKeyStr(i));			
				if(null!=tmpAra){	for(int j =0; j<tmpAra.size();++j){	if(tmpAra.get(j).clickedMe(mse)){return tmpAra.get(j);}}}
			}	
		}
		return null;		
	}
	
	//stuff to do when shown/hidden
	public void setShow(boolean val){
		setFlags(showIDX,val);
		setClosedBox();
		if(!getFlags(showIDX)){//not showing window
			closeMe();//specific instancing window implementation stuff to do when hidden
		} else {
			showMe();//specific instance window functionality to do when window is shown
		}
	}
	
	protected void toggleWindowState(){
		//pa.outStr2Scr("Attempting to close window : " + this.name);
		setFlags(showIDX,!getFlags(showIDX));
		pa.setBaseFlag(pFlagIdx, getFlags(showIDX));		//value has been changed above by close box
	}
	
	protected boolean checkClsBox(int mouseX, int mouseY){
		boolean res = false;
		if(pa.ptInRange(mouseX, mouseY, closeBox[0], closeBox[1], closeBox[0]+closeBox[2], closeBox[1]+closeBox[3])){toggleWindowState(); res = true;}				
		return res;		
	}
	//check if mouse location is in UI buttons, and handle button click if so
	protected boolean checkUIButtons(int mouseX, int mouseY){
		boolean mod = false;
		int mx, my;
		//keep checking -see if clicked in UI buttons (flag-based buttons)
		for(int i = 0;i<privFlagBtns.length;++i){//rectDim[0], rectDim[1]  mseClickCrnr
			//mx = (int)(mouseX - rectDim[0]); my = (int)(mouseY - rectDim[1]);
			mx = (int)(mouseX - mseClickCrnr[0]); my = (int)(mouseY - mseClickCrnr[1]);
			mod = msePtInRect(mx, my, privFlagBtns[i]); 
			//pa.outStr2Scr("Handle mouse click in window : "+ ID + " : (" + mouseX+","+mouseY+") : "+mod + ": btn rect : "+privFlagBtns[i][0]+","+privFlagBtns[i][1]+","+privFlagBtns[i][2]+","+privFlagBtns[i][3]);
			if (mod){ 
				setPrivFlags(privModFlgIdxs[i],!getPrivFlags(privModFlgIdxs[i])); 
				return mod;
			}			
		}
		return mod;
	}//checkUIButtons	
	
	//change view based on mouse click/drag behavior and whether we are moving or zooming
	//use delX for zoom
	public void handleViewChange(boolean doZoom, float delX, float delY ) {
		if(doZoom) {	dz-=delX;	} 
		else {			rx-=delX; ry+=delY;} 		
	}//handleViewChange()
	
	//modify the viewing target by finding -TODO doesn't behave appropriately if camera is rotated around origin
	public void handleViewTargetChange(float delY, float delX) {
		//find screen up unit vector, screen right unit vector in world space, dot focus tar with that, move in that direction
		setCamOrient();
		
		if(Math.abs(delY) > 0) {
			myVectorf scrUp = pa.c.getUScrUpInWorldf();//, upVec = myVectorf._cross(scrRt, scrUp);
			myVectorf up = new myVectorf(scrUp.x* -delY,scrUp.y* -delY,scrUp.z* -delY);
			focusTar._add(up);
		}		
		if(Math.abs(delX) > 0) {
			myVectorf scrRt = pa.c.getUScrRightInWorldf();
			myVectorf rt = new myVectorf(scrRt.x* delX,scrRt.y* delX,scrRt.z* delX);
			focusTar._add(rt);
		}
		unSetCamOrient();
	}//handleViewTargetChange
	
	protected myPoint getMsePoint(myPoint pt){return getFlags(myDispWindow.is3DWin) ? getMsePtAs3DPt((int)pt.x, (int)pt.y) : pt;}
	protected myPoint getMsePoint(int mouseX, int mouseY){return getFlags(myDispWindow.is3DWin) ? getMsePtAs3DPt(mouseX, mouseY) : pa.P(mouseX,mouseY,0);}
	public boolean handleMouseMove(int mouseX, int mouseY){
		if(!getFlags(showIDX)){return false;}
		if((getFlags(showIDX))&& (msePtInUIRect(mouseX, mouseY))){//in clickable region for UI interaction
			for(int j=0; j<guiObjs.length; ++j){if(guiObjs[j].checkIn(mouseX, mouseY)){	msOvrObj=j;return true;	}}
		}
		myPoint mouseClickIn3D = pa.c.getMseLoc(sceneCtrVal);
		if(hndlMouseMoveIndiv(mouseX, mouseY, mouseClickIn3D)){return true;}
		msOvrObj = -1;
		return false;
	}//handleMouseMove
	
	public boolean msePtInRect(int x, int y, float[] r){return ((x > r[0])&&(x <= r[0]+r[2])&&(y > r[1])&&(y <= r[1]+r[3]));}	
	public boolean msePtInUIRect(int x, int y){return ((x > uiClkCoords[0])&&(x <= uiClkCoords[2])&&(y > uiClkCoords[1])&&(y <= uiClkCoords[3]));}	

	public boolean handleMouseClick(int mouseX, int mouseY, int mseBtn){
		boolean mod = false;
		if((getFlags(showIDX))&& (msePtInUIRect(mouseX, mouseY))){//in clickable region for UI interaction
			for(int j=0; j<guiObjs.length; ++j){
				if(guiObjs[j].checkIn(mouseX, mouseY)){	
					msBtnClcked = mseBtn;
					if(pa.isClickModUIVal()){//allows for click-mod
						setUIObjValFromClickAlone(j);
						//float mult = msBtnClcked * -2.0f + 1;	//+1 for left, -1 for right btn	
						//pa.outStr2Scr("Mult : " + (mult *pa.clickValModMult()));
						//guiObjs[j].modVal(mult * pa.clickValModMult());
						setFlags(uiObjMod,true);
					} //else {										//has drag mod					
					msClkObj=j;
					//}
					return true;	
				}
			}
		}			
		if(getFlags(closeable)){mod = checkClsBox(mouseX, mouseY);}							//check if trying to close or open the window via click, if possible
		if(!getFlags(showIDX)){return mod;}
		if(!mod){
			myPoint mouseClickIn3D = pa.c.getMseLoc(sceneCtrVal);
			mod = hndlMouseClickIndiv(mouseX, mouseY,mouseClickIn3D, mseBtn);
		}			//if nothing triggered yet, then specific instancing window implementation stuff
		if((!mod) && (msePtInRect(mouseX, mouseY, this.rectDim)) && (getFlags(canDrawTraj))){ 
			myPoint pt =  getMsePoint(mouseX, mouseY);
			if(null==pt){return false;}
			mod = handleTrajClick(pa.altIsPressed(), pt);}			//click + alt for traj drawing : only allow drawing trajectory if it can be drawn and no other interaction has occurred
		return mod;
	}//handleMouseClick
	//vector for drag in 3D
	public boolean handleMouseDrag(int mouseX, int mouseY,int pmouseX, int pmouseY, myVector mseDragInWorld, int mseBtn){
		boolean mod = false;
		if(!getFlags(showIDX)){return mod;}
		//check if modding view
		if ((pa.shiftIsPressed()) && getFlags(canChgView) && (msClkObj==-1)) {//modifying view angle/zoom
			pa.setModView(true);	
			if(mseBtn == 0){			handleViewChange(false,pa.msSclY*(mouseY-pmouseY), pa.msSclX*(mouseX-pmouseX));}	
			else if (mseBtn == 1) {		handleViewChange(true,(mouseY-pmouseY), 0);}	//moveZoom(mouseY-pmouseY);}//dz-=(
			return true;
		} else if ((pa.cntlIsPressed()) && getFlags(canChgView) && (msClkObj==-1)) {//modifying view focus
			pa.setModView(true);
			handleViewTargetChange((mouseY-pmouseY), (mouseX-pmouseX));
			return true;
		} else {//modify UI elements
		
			//any generic dragging stuff - need flag to determine if trajectory is being entered		
			//modify object that was clicked in by mouse motion
			if(msClkObj!=-1){	guiObjs[msClkObj].modVal((mouseX-pmouseX)+(mouseY-pmouseY)*-(pa.shiftIsPressed() ? 50.0f : 5.0f));setFlags(uiObjMod, true); return true;}		
			if(getFlags(drawingTraj)){ 		//if drawing trajectory has started, then process it
				//pa.outStr2Scr("drawing traj");
				myPoint pt =  getMsePoint(mouseX, mouseY);
				if(null==pt){return false;}
				this.tmpDrawnTraj.addPoint(pt);
				mod = true;
			}else if(getFlags(editingTraj)){		//if editing trajectory has started, then process it
				//pa.outStr2Scr("edit traj");	
				myPoint pt =  getMsePoint(mouseX, mouseY);
				if(null==pt){return false;}
				mod = this.tmpDrawnTraj.editTraj(mouseX, mouseY,pmouseX, pmouseY,pt,mseDragInWorld);
			}
			else {
				if((!pa.ptInRange(mouseX, mouseY, rectDim[0], rectDim[1], rectDim[0]+rectDim[2], rectDim[1]+rectDim[3]))){return false;}	//if not drawing or editing a trajectory, force all dragging to be within window rectangle
				//pa.outStr2Scr("before handle indiv drag traj for window : " + this.name);
				myPoint mouseClickIn3D = pa.c.getMseLoc(sceneCtrVal);
				mod = hndlMouseDragIndiv(mouseX, mouseY,pmouseX, pmouseY,mouseClickIn3D,mseDragInWorld,mseBtn);		//handle specific, non-trajectory functionality for implementation of window
			}
		}
		return mod;
	}//handleMouseDrag
	
	//set all window values for UI objects
	private void setAllUIWinVals() {for(int i=0;i<guiObjs.length;++i){if(guiObjs[i].getFlags(myGUIObj.usedByWinsIDX)){setUIWinVals(i);}}}
	//set UI value for object based on non-drag modification such as click - either at initial click or when click is released
	private void setUIObjValFromClickAlone(int j) {
		float mult = msBtnClcked * -2.0f + 1;	//+1 for left, -1 for right btn	
		//pa.outStr2Scr("Mult : " + (mult *pa.clickValModMult()));
		guiObjs[j].modVal(mult * pa.clickValModMult());
	}//setUIObjValFromClickAlone
	
	public void handleMouseRelease(){
		if(!getFlags(showIDX)){return;}
		if(getFlags(uiObjMod)){
			setAllUIWinVals();
			setFlags(uiObjMod, false);
			msClkObj = -1;	
		}//some object was clicked - pass the values out to all windows
		else if(msClkObj != -1) {//means object was clicked in but not drag modified through drag or shift-clic - use this to modify by clicking
			setUIObjValFromClickAlone(msClkObj);
			setAllUIWinVals();
			setFlags(uiObjMod, false);
			msClkObj = -1;	
		}
		if (getFlags(editingTraj)){    this.tmpDrawnTraj.endEditObj();}    //this process assigns tmpDrawnTraj to owning window's traj array
		if (getFlags(drawingTraj)){	this.tmpDrawnTraj.endDrawObj(getMsePoint(pa.Mouse()));}	//drawing curve
		msClkObj = -1;	
		//if buttons have put in clear queue (set to clear), set flag to clear them next draw
		if (privBtnsToClear.size() > 0){setFlags(clearPrivBtns, true);	}		
		hndlMouseRelIndiv();//specific instancing window implementation stuff

		this.tmpDrawnTraj = null;
	}//handleMouseRelease	
	
	//release shift/control/alt keys
	public void endShiftKey(){
		if(!getFlags(showIDX)){return;}
		//
		endShiftKeyI();
	}
	public void endAltKey(){
		if(!getFlags(showIDX)){return;}
		if(getFlags(drawingTraj)){this.tmpDrawnTraj.endDrawObj(getMsePoint(pa.Mouse()));}	
		endAltKeyI();
		this.tmpDrawnTraj = null;
	}	
	public void endCntlKey(){
		if(!getFlags(showIDX)){return;}
		//
		endCntlKeyI();
	}	
			
	//drawn trajectory stuff	
	public void startBuildDrawObj(){
		pa.setIsDrawing(true);
		//drawnTrajAra[curDrnTrajScrIDX][curDrnTrajStaffIDX].startBuildTraj();
		tmpDrawnTraj= new myDrawnSmplTraj(pa,this,topOffY,trajFillClrCnst, trajStrkClrCnst, getFlags(trajPointsAreFlat), !getFlags(trajPointsAreFlat));
		tmpDrawnTraj.startBuildTraj();
		setFlags(drawingTraj, true);
	}
	
	//finds closest point to p in sPts - put dist in d
	public final int findClosestPt(myPoint p, double[] d, myPoint[] _pts){
		int res = -1;
		double mindist = 99999999, _d;
		for(int i=0; i<_pts.length; ++i){_d = myPoint._dist(p,_pts[i]);if(_d < mindist){mindist = _d; d[0]=_d;res = i;}}	
		return res;
	}

	//initialize circle so that it will draw at location of edit
	public void setEditCueCircle(int idx,myPoint mse){
		setFlags(showTrajEditCrc, true);
		editCrcCtrs[idx].set(mse.x, mse.y, 0);
		editCrcCurRads[idx] = editCrcRads[idx];
	}
	
	public void rebuildAllDrawnTrajs(){
		for(TreeMap<String,ArrayList<myDrawnSmplTraj>> tmpTreeMap : drwnTrajMap.values()){
			if((tmpTreeMap != null) && (tmpTreeMap.size() != 0)) {
				for(int i =0; i<tmpTreeMap.size(); ++i){
					ArrayList<myDrawnSmplTraj> tmpAra = tmpTreeMap.get(getTrajAraKeyStr(i));			
					if(null!=tmpAra){	for(int j =0; j<tmpAra.size();++j){	tmpAra.get(j).rebuildDrawnTraj();}}
				}
			}	
		}			
	}//rebuildAllDrawnTrajs
	
	//debug data to display on screen
	//get string array for onscreen display of debug info for each object
	public String[] getDebugData(){
		ArrayList<String> res = new ArrayList<String>();
		List<String>tmp;
		for(int j = 0; j<guiObjs.length; j++){tmp = Arrays.asList(guiObjs[j].getStrData());res.addAll(tmp);}
		return res.toArray(new String[0]);	
	}
	
	//setup the launch of UI-driven custom functions or debugging capabilities, which will execute next frame
	
	//set colors of the trajectory for this window
	public void setTrajColors(int _tfc, int _tsc){trajFillClrCnst = _tfc;trajStrkClrCnst = _tsc;initTmpTrajStuff(getFlags(trajPointsAreFlat));}
	//get key used to access arrays in traj array
	protected String getTrajAraKeyStr(int i){return trajNameAra[i];}
	protected int getTrajAraIDXVal(String str){for(int i=0; i<trajNameAra.length;++i){if(trajNameAra[i].equals(str)){return i;}}return -1; }

	//add trajectory to appropriately keyed current trajectory ara in treemap	
	protected void processTrajectory(myDrawnSmplTraj drawnNoteTraj){
		TreeMap<String,ArrayList<myDrawnSmplTraj>> tmpTreeMap = drwnTrajMap.get(this.curDrnTrajScrIDX);
		ArrayList<myDrawnSmplTraj> tmpAra;
		if(curTrajAraIDX != -1){		//make sure some trajectory/staff has been selected
			if((tmpTreeMap != null) && (tmpTreeMap.size() != 0) ) {
				tmpAra = tmpTreeMap.get(getTrajAraKeyStr(curTrajAraIDX));			
				//if((null==tmpAra) || (pa.flags[pa.clearTrajWithNew])){	
				//for this application always wish to clear traj
					tmpAra = new ArrayList<myDrawnSmplTraj>();
				//}
				//lastTrajIDX = tmpAra.size();
				tmpAra.add(drawnNoteTraj); 				
			} else {//empty or null tmpTreeMap - tmpAra doesn't exist
				tmpAra = new ArrayList<myDrawnSmplTraj>();
				tmpAra.add(drawnNoteTraj);
				//lastTrajIDX = tmpAra.size();
				if(tmpTreeMap == null) {tmpTreeMap = new TreeMap<String,ArrayList<myDrawnSmplTraj>>();} 
			}
			tmpTreeMap.put(getTrajAraKeyStr(curTrajAraIDX), tmpAra);
			processTrajIndiv(drawnNoteTraj);
		}	
		//individual traj processing
	}
	
	public void clearAllTrajectories(){//int instrIdx){
		TreeMap<String,ArrayList<myDrawnSmplTraj>> tmpTreeMap = drwnTrajMap.get(this.curDrnTrajScrIDX);
		ArrayList<myDrawnSmplTraj> tmpAra;
		if(curTrajAraIDX != -1){		//make sure some trajectory/staff has been selected
			if((tmpTreeMap != null) && (tmpTreeMap.size() != 0) ) {
				tmpTreeMap.put(getTrajAraKeyStr(curTrajAraIDX), new ArrayList<myDrawnSmplTraj>());
			}
		}	
	}//clearAllTrajectories
	
	//add another screen to this window - need to handle specific trajectories - always remake traj structure
	public void addSubScreenToWin(int newWinKey){
		modTrajStructs(newWinKey, "",false);
		
		addSScrToWinIndiv(newWinKey);
	}
	public void addTrajToSubScreen(int subScrKey, String newTrajKey){
		modTrajStructs(subScrKey, newTrajKey,false);		
		addTrajToScrIndiv(subScrKey, newTrajKey);
	}
	
	public void delSubScreenToWin(int delWinKey){
		modTrajStructs(delWinKey, "",true);		
		delSScrToWinIndiv(delWinKey);
	}
	public void delTrajToSubScreen(int subScrKey, String newTrajKey){
		modTrajStructs(subScrKey, newTrajKey,true);
		delTrajToScrIndiv(subScrKey,newTrajKey);
	}
		
	//updates values in UI with programatic changes 
	public boolean setWinToUIVals(int UIidx, double val){return val == guiObjs[UIidx].setVal(val);}	
	//UI controlled auxiliary/debug functionality	
	public final void clickSideMenuBtn(int _typeIDX, int btnNum) {	curCustBtnType = _typeIDX; curCustBtn[_typeIDX] = btnNum; custClickSetThisFrame = true;}
	
	//check if either custom function or debugging has been launched and process if so, skip otherwise.latched by a frame so that button can be turned on
	public final void checkCustMenuUIObjs() {
		if (custClickSetThisFrame) { custClickSetThisFrame = false;custFuncDoLaunch=true;return;}	//was set last frame and processed, so clear all flags
		if (!custFuncDoLaunch) {return;}//has been launched don't relaunch
		launchMenuBtnHndlr();
		custFuncDoLaunch=false;
	}//checkCustMenuUIObjs

	//type is row of buttons (1st idx in curCustBtn array) 2nd idx is btn
	protected abstract void launchMenuBtnHndlr() ;
	
	//call from custFunc/custDbg functions being launched in threads
	//these are launched in threads to allow UI to respond to user input
	public void resetButtonState() {resetButtonState(true);}
	public void resetButtonState(boolean isSlowProc) {
		if (curCustBtn[curCustBtnType] == -1) {return;}
		pa.clearBtnState(curCustBtnType,curCustBtn[curCustBtnType], isSlowProc);
		curCustBtn[curCustBtnType] = -1;
	}//resetButtonState
	
	//return relevant name information for files and directories to be used to build screenshots/saved files	
	protected abstract String[] getSaveFileDirNamesPriv();
	
	protected abstract void initDrwnTrajIndiv();
	protected abstract void addSScrToWinIndiv(int newWinKey);
	protected abstract void addTrajToScrIndiv(int subScrKey, String newTrajKey);
	protected abstract void delSScrToWinIndiv(int idx);
	protected abstract void delTrajToScrIndiv(int subScrKey, String newTrajKey);
	
	protected abstract myPoint getMsePtAs3DPt(int mouseX, int mouseY);	
	//set window-specific variables that are based on current visible screen dimensions
	protected abstract void setVisScreenDimsPriv();
	//implementing class' necessary functions - implement for each individual window
	protected abstract boolean hndlMouseMoveIndiv(int mouseX, int mouseY, myPoint mseClckInWorld);
	protected abstract boolean hndlMouseClickIndiv(int mouseX, int mouseY, myPoint mseClckInWorld, int mseBtn);
	protected abstract boolean hndlMouseDragIndiv(int mouseX, int mouseY,int pmouseX, int pmouseY, myPoint mouseClickIn3D, myVector mseDragInWorld, int mseBtn);
	protected abstract void snapMouseLocs(int oldMouseX, int oldMouseY, int[] newMouseLoc);	
	
	protected abstract void hndlMouseRelIndiv();
	
	protected abstract void endShiftKeyI();
	protected abstract void endAltKeyI();
	protected abstract void endCntlKeyI();
	
	//ui init routines
	protected abstract void setupGUIObjsAras();	
	protected abstract void setUIWinVals(int UIidx);		//set prog values from ui
	protected abstract String getUIListValStr(int UIidx, int validx);
	protected abstract void processTrajIndiv(myDrawnSmplTraj drawnTraj);
	
	//file io used from selectOutput/selectInput - 
	//take loaded params and process
	public abstract void hndlFileLoad(File file, String[] vals, int[] stIdx);
	//accumulate array of params to save
	public abstract ArrayList<String> hndlFileSave(File file);	
	
	protected abstract void initMe();
	protected abstract void resizeMe(float scale);	
	protected abstract void showMe();
	protected abstract void closeMe();	
	protected abstract boolean simMe(float modAmtSec);
	protected abstract void stopMe();
	protected abstract void setCameraIndiv(float[] camVals);
	protected abstract void drawMe(float animTimeMod);	
	protected abstract void drawRightSideInfoBarPriv(float modAmtMillis);
	protected abstract void drawOnScreenStuffPriv(float modAmtMillis);
	
	public String toString(){
		String res = "Window : "+name+" ID: "+ID+" Fill :("+fillClr[0]+","+fillClr[1]+","+fillClr[2]+","+fillClr[3]+
				") | Stroke :("+fillClr[0]+","+fillClr[1]+","+fillClr[2]+","+fillClr[3]+") | Rect : ("+
				String.format("%.2f",rectDim[0])+","+String.format("%.2f",rectDim[1])+","+String.format("%.2f",rectDim[2])+","+String.format("%.2f",rectDim[3])+")\n";	
		return res;
	}
}//dispWindow




class myScrollBars{
	public my_procApplet pa;
	public myDispWindow win;
	public static int scrBarCnt = 0;
	public int ID;
	//displacement for scrolling display - x/y location of window, x/y zoom - use scrollbars&zoomVals - displacement is translate, zoom is scale
	public float[] scrollZoomDisp,
	//start x,y, width, height of scroll bar region 
	 	hScrlDims,	
	//start x,y, width, height of scroll bar region 
	 	vScrlDims;
	private final float thk = 20, thmult = 1.5f;				//scrollbar dim is 25 pxls wide	
	
	public float[][] arrowDims = new float[][]{{0,0,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,0}};
	//hthumb x,y width,height,vthumb x,y width,height
	public float[][] thmbs = new float[][]{{0,0},{0,0}};
	//hthumb min/max, vthumb min/max bounds
	public float[][] thmbBnds = new float[][]{{0,0},{0,0}};
	
	public static final int
		upIDX = 0,
		dnIDX = 1,
		ltIDX = 2,
		rtIDX = 3,
		hThmbIDX = 0,
		vThmbIDX = 1;
	
	public int[][] clrs;			//colors for thumb and up/down/left/right arrows
	
	public myScrollBars(my_procApplet _pa,myDispWindow _win){
		pa = _pa;
		win = _win;
		ID = scrBarCnt++;
		setSize();
		clrs = new int[][]{ pa.getRndClr(),pa.getRndClr(),pa.getRndClr(),pa.getRndClr(),pa.getRndClr(),pa.getRndClr()};
	}//myScrollBars
	
	public void setSize(){
		float rectWidth = win.rectDim[0]+win.rectDim[2],vScrlStartY = win.rectDim[1]+(win.closeBox[3]),
				rectHeight = win.rectDim[3]-(vScrlStartY);
		vScrlDims = new float[]{rectWidth - thk,vScrlStartY, thk,  rectHeight-thk};
		hScrlDims = new float[]{win.rectDim[0], win.rectDim[1]+win.rectDim[3] - thk,win.rectDim[2]-thk,thk};

		thmbs[hThmbIDX] = new float[]{hScrlDims[0]+thk,hScrlDims[1],thmult*thk,thk};			//location/dims of thumb
		thmbBnds[hThmbIDX] = new float[]{thmbs[hThmbIDX][0],hScrlDims[2]-thmbs[hThmbIDX][2]-thk};		//min/max x val of horiz thumb
		
		thmbs[vThmbIDX] = new float[]{vScrlDims[0],(vScrlDims[1]+thk),thk,thmult*thk};			//location/dims of thumb
		thmbBnds[vThmbIDX] = new float[]{thmbs[vThmbIDX][1],vScrlDims[3]-thmbs[vThmbIDX][3]-thk};		//min/max of y val of vert thumb
		
		//arrow boxes - x,y,widht,height
		arrowDims = new float[][]{
			{vScrlDims[0],vScrlDims[1],thk,thk},			//up
			{vScrlDims[0],vScrlDims[1]+vScrlDims[3]-thk,thk,thk},		//down
			{hScrlDims[0],					hScrlDims[1],thk,thk},			//left
			{hScrlDims[0]+hScrlDims[2]-thk,hScrlDims[1],thk,thk}};		//right

	}
	public void drawMe(){
		pa.pushMatrix(); pa.pushStyle();
		pa.setColorValFill(pa.gui_LightGray);
		pa.setColorValStroke(pa.gui_Black);
		pa.strokeWeight(1.0f);
		pa.rect(vScrlDims);
		pa.rect(hScrlDims);
		for(int i =0; i<arrowDims.length;++i){
			pa.setFill(clrs[i]);
			pa.rect(arrowDims[i]);
		}
		pa.popStyle();pa.popMatrix();
		pa.pushMatrix(); pa.pushStyle();
		for(int i =0; i<thmbs.length;++i){
			pa.setFill(clrs[i + 4]);
			pa.rect(thmbs[i]);
		}
		
		pa.popStyle();pa.popMatrix();
	}//drawMe
	
	
	public boolean msePtInRect(int x, int y, float[] r){return ((x > r[0])&&(x <= r[0]+r[2])&&(y > r[1])&&(y <= r[1]+r[3]));}		
	public boolean handleMouseClick(int mouseX, int mouseY, myPoint mouseClickIn3D){
		
		return false;	
	}//handleMouseClick
	public boolean handleMouseDrag(int mouseX, int mouseY,int pmouseX, int pmouseY, myPoint mouseClickIn3D, myVector mseDragInWorld){	
		
		return false;
	}//handleMouseDrag
	public void handleMouseRelease(){
		
		
	}//handleMouseRelease
	
}//myScrollBars

