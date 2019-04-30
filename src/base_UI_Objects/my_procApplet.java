package base_UI_Objects;

import java.io.File;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

import base_Utils_Objects.*;
import processing.core.PApplet;
import processing.core.PConstants;
import processing.event.MouseEvent;

public abstract class my_procApplet extends PApplet {
	
	protected int glblStartSimFrameTime,			//begin of draw
		glblLastSimFrameTime,					//begin of last draw
		glblStartProgTime;					//start of program
	
	//static variables - put obj constructor counters here
	public static int GUIObjID = 0;										//counter variable for gui objs

	public int drawnTrajEditWidth = 10; //TODO make ui component			//width in cntl points of the amount of the drawn trajectory deformed by dragging
	
	//individual display/HUD windows for gui/user interaction
	protected myDispWindow[] dispWinFrames;
	//set in instancing class - must be > 1
	protected int numDispWins;
	//always idx 0 - first window is always menu
	public static final int dispMenuIDX = 0;	
	//which myDispWindow currently has focus
	public int curFocusWin;		
	//need 1 per display window
	public String[] winTitles,winDescr;

	//whether or not the display windows will accept a drawn trajectory
	protected boolean[][] dispWinFlags;
	//idxs : 0 : canDrawInWin; 1 : canShow3dbox; 2 : canMoveView; 3 : dispWinIs3d
	protected static int
		dispCanDrawInWinIDX 	= 0,
		dispCanShow3dboxIDX 	= 1,
		dispCanMoveViewIDX 		= 2,
		dispWinIs3dIDX 			= 3;
	private static int numDispWinBoolFlags = 4;
	
	public int[][] winFillClrs;
	public int[][] winStrkClrs;
	
	public int[] winTrajFillClrs = new int []{0,0};		//set to color constants for each window
	public int[] winTrajStrkClrs = new int []{0,0};		//set to color constants for each window
	

	//specify windows that cannot be shown simultaneously here and their flags
	public int[] winFlagsXOR;	
	public int[] winDispIdxXOR;
	
	//unblocked window dimensions - location and dim of window if window is one\
	public float[][] winRectDimOpen;// = new float[][]{new float[]{0,0,0,0},new float[]{0,0,0,0},new float[]{0,0,0,0},new float[]{0,0,0,0}};
	//window dimensions if closed -location and dim of all windows if this window is closed
	public float[][] winRectDimClose;// = new float[][]{new float[]{0,0,0,0},new float[]{0,0,0,0},new float[]{0,0,0,0},new float[]{0,0,0,0}};

	//used to manage current time
	public Calendar now;
	//data being printed to console - show on screen
	public ArrayDeque<String> consoleStrings;							

	public int[] rgbClrs = new int[]{gui_Red,gui_Green,gui_Blue};
	
	protected final int cnslStrDecay = 10;			//how long a message should last before it is popped from the console strings deque (how many frames)
	
	public final float frate = 120;			//frame rate - # of playback updates per second
	
	//size of printed text (default is 12)
	public static final int txtSz = 11;
	//mouse wheel sensitivity
	public static final float mouseWhlSens = 1.0f;

	//display-related size variables
	public final int grid2D_X=800, grid2D_Y=800;	
	public final int gridDimX = 800, gridDimY = 800, gridDimZ = 800;				//dimensions of 3d region
	//boundary regions for enclosing cube - given as min and difference of min and max
	public float[][] cubeBnds = new float[][]{//idx 0 is min, 1 is diffs
		new float[]{-gridDimX/2.0f,-gridDimY/2.0f,-gridDimZ/2.0f},//mins
		new float[]{gridDimX,gridDimY,gridDimZ}};			//diffs
		
	
	//2D, 3D
	private myVector[] sceneFcsValsBase = new myVector[]{						//set these values to be different targets of focus
			new myVector(-grid2D_X/2,-grid2D_Y/1.75f,0),
			new myVector(0,0,0)
	};
	//2D, 3D
	private myPoint[] sceneCtrValsBase = new myPoint[]{				//set these values to be different display center translations -
		new myPoint(0,0,0),										// to be used to calculate mouse offset in world for pick
		new myPoint(-gridDimX/2.0,-gridDimY/2.0,-gridDimZ/2.0)
	};
		
	
	public final float
	//PopUpWinOpenFraction = .40f,				//fraction of screen not covered by popwindow
		wScale = frameRate/5.0f,					//velocity drag scaling	
		trajDragScaleAmt = 100.0f;					//amt of displacement when dragging drawn trajectory to edit
	
	// path and filename to save pictures for animation
	public int animCounter, runCounter;	
	public final int scrMsgTime = 50;									//5 seconds to delay a message 60 fps (used against draw count)
	
	protected int drawCount,simCycles;												// counter for draw cycles		
	protected float menuWidth,menuWidthMult = .15f, hideWinWidth, hideWinWidthMult = .03f, hidWinHeight, hideWinHeightMult = .05f;			//side menu is 15% of screen grid2D_X, 
	
	private ArrayList<String> DebugInfoAra;										//enable drawing dbug info onto screen
	private String debugInfoString;
	
	//animation control variables	
	public final float maxAnimCntr = PI*1000.0f, baseAnimSpd = 1.0f;
	public float msSclX, msSclY;											//scaling factors for mouse movement		
	public my3DCanvas c;												//3d interaction stuff and mouse tracking
	
	private float dz=0, rx=-0.06f*TWO_PI, ry=-0.04f*TWO_PI;		// distance to camera. Manipulated with wheel or when,view angles manipulated when space pressed but not mouse	
	public final float camInitialDist = -200,		//initial distance camera is from scene - needs to be negative
			camInitRy = ry,
			camInitRx = rx;
	public float[] camVals;		
	
	public double eps = .000000001, msClkEps = 40;				//calc epsilon, distance within which to check if clicked from a point
	public float feps = .000001f;
	public float SQRT2 = sqrt(2.0f);
	
	//visualization variables
	// boolean flags used to control various elements of the program 
	private int[] baseFlags;
	//dev/debug flags
	private final int debugMode 	= 0,			//whether we are in debug mode or not	
			 finalInitDone		= 1,			//used only to call final init in first draw loop, to avoid stupid timeout error processing 3.x's setup introduced
			 saveAnim 			= 2,			//whether we are saving or not
	//interface flags	                   ,
			 shiftKeyPressed 	= 3,			//shift pressed
			 altKeyPressed  	= 4,			//alt pressed
			 cntlKeyPressed  	= 5,			//cntrl pressed
			 mouseClicked 		= 6,			//mouse left button is held down	
			 drawing			= 7, 			//currently drawing  showSOMMapUI
			 modView	 		= 8,			//shift+mouse click+mouse move being used to modify the view
	//simulation
			 runSim				= 9,			//run simulation
			 singleStep			= 10,			//run single sim step
			 showRtSideMenu		= 11,			//display the right side info menu for the current window, if it supports that display
			 flipDrawnTraj  	= 12;			//whether or not to flip the direction of the drawn trajectory
			
	public final int numBaseFlags = 13;
	
	public final int numDebugVisFlags = 6;
	//flags to actually display in menu as clickable text labels - order does matter
	public List<Integer> flagsToShow = Arrays.asList( 
		debugMode, 			
		saveAnim,
		runSim,
		singleStep,
		showRtSideMenu
		);
	
	public final int numFlagsToShow = flagsToShow.size();
	
	public List<Integer> stateFlagsToShow = Arrays.asList( 
		shiftKeyPressed,			//shift pressed
		altKeyPressed,				//alt pressed
		cntlKeyPressed,				//cntrl pressed
		mouseClicked,				//mouse left button is held down	
		drawing, 					//currently drawing
		modView	 					//shift+mouse click+mouse move being used to modify the view					
			);
	public final int numStFlagsToShow = stateFlagsToShow.size();	
	
	//3dbox stuff
	public myVector[] boxNorms = new myVector[] {new myVector(1,0,0),new myVector(-1,0,0),new myVector(0,1,0),new myVector(0,-1,0),new myVector(0,0,1),new myVector(0,0,-1)};//normals to 3 d bounding boxes
	protected final float hGDimX = gridDimX/2.0f, hGDimY = gridDimY/2.0f, hGDimZ = gridDimZ/2.0f;
	protected final float tGDimX = gridDimX*10, tGDimY = gridDimY*10, tGDimZ = gridDimZ*20;
	public final myPoint[][] boxWallPts = new myPoint[][] {//pts to check if intersection with 3D bounding box happens
			new myPoint[] {new myPoint(hGDimX,tGDimY,tGDimZ), new myPoint(hGDimX,-tGDimY,tGDimZ), new myPoint(hGDimX,tGDimY,-tGDimZ)  },
			new myPoint[] {new myPoint(-hGDimX,tGDimY,tGDimZ), new myPoint(-hGDimX,-tGDimY,tGDimZ), new myPoint(-hGDimX,tGDimY,-tGDimZ) },
			new myPoint[] {new myPoint(tGDimX,hGDimY,tGDimZ), new myPoint(-tGDimX,hGDimY,tGDimZ), new myPoint(tGDimX,hGDimY,-tGDimZ) },
			new myPoint[] {new myPoint(tGDimX,-hGDimY,tGDimZ),new myPoint(-tGDimX,-hGDimY,tGDimZ),new myPoint(tGDimX,-hGDimY,-tGDimZ) },
			new myPoint[] {new myPoint(tGDimX,tGDimY,hGDimZ), new myPoint(-tGDimX,tGDimY,hGDimZ), new myPoint(tGDimX,-tGDimY,hGDimZ)  },
			new myPoint[] {new myPoint(tGDimX,tGDimY,-hGDimZ),new myPoint(-tGDimX,tGDimY,-hGDimZ),new myPoint(tGDimX,-tGDimY,-hGDimZ)  }
	};
	
	//whether or not to show start up instructions for code		
	public boolean showInfo=false;			
	
	protected String exeDir = Paths.get(".").toAbsolutePath().toString();
	//file location of current executable
	protected File currFileIOLoc = Paths.get(".").toAbsolutePath().toFile();
	
	////////////////////////
	// code
	
	///////////////////////////////////
	/// inits
	///////////////////////////////////
	
	public final void setup() {
		colorMode(RGB, 255, 255, 255, 255);
		frameRate(frate);
		setup_indiv();
		initVisOnce();
		//call this in first draw loop?
		initOnce();		
	}//setup()
	
	protected abstract void setup_indiv();
		//1 time initialization of visualization things that won't change
	public final void initVisOnce(){	
		int numThreadsAvail = Runtime.getRuntime().availableProcessors();
		
		now = Calendar.getInstance();
		//mouse scrolling scale
		msSclX = (float) (Math.PI/width);
		msSclY = (float) (Math.PI/height);

		consoleStrings = new ArrayDeque<String>();				//data being printed to console		
		outStr2Scr("# threads : "+ numThreadsAvail);
		outStr2Scr("Current sketchPath " + sketchPath());
		
		menuWidth = width * menuWidthMult;						//grid2D_X of menu region	
		hideWinWidth = width * hideWinWidthMult;				//dims for hidden windows
		hidWinHeight = height * hideWinHeightMult;
		c = new my3DCanvas(this);			
		strokeCap(SQUARE);//makes the ends of stroke lines squared off		
		//instancing class version
		initVisOnce_Priv();
		
		//after all display windows are drawn
		finalDispWinInit();
		initVisFlags();
		
		//init initernal state flags structure
		initBaseFlags();			
		//camVals = new float[]{width/2.0f, height/2.0f, (height/2.0f) / tan(PI/6.0f), width/2.0f, height/2.0f, 0, 0, 1, 0};
		camVals = new float[]{0, 0, (height/2.0f) / tan(PI/6.0f), 0, 0, 0, 0,1,0};
		
		textSize(txtSz);
		
		textureMode(NORMAL);			
		rectMode(CORNER);	
		sphereDetail(4);
		simCycles = 0;
		glblStartProgTime = millis();
		glblStartSimFrameTime = glblStartProgTime;
		glblLastSimFrameTime =  glblStartProgTime;	

		initCamView();
		simCycles = 0;

	}//	initVisOnce
	protected abstract void initVisOnce_Priv();
		//1 time initialization of programmatic things that won't change
	public final void initOnce() {
		//th_exec = Executors.newCachedThreadPool();
		//1-time init for program and windows
		initOnce_Priv();
		//initProgram is called every time reinitialization is desired
		initProgram();		

		//after all init is done
		setFinalInitDone(true);
	}//initOnce	
//	protected abstract void initDispWins();
	protected abstract void initOnce_Priv();
	
	
		//called every time re-initialized
	public final void initVisProg(){	
		drawCount = 0;		
		debugInfoString = "";		
		reInitInfoStr();
	}
	protected abstract void initVisProg_Indiv();
		//called every time re-initialized
	public final void initProgram() {
		initVisProg();				//always first
		
		initProgram_Indiv();

	}//initProgram	
	protected abstract void initProgram_Indiv();
	
	
	public final void initCamView(){	dz=camInitialDist;	ry=camInitRy;	rx=camInitRx - ry;	}
	public final void reInitInfoStr(){		DebugInfoAra = new ArrayList<String>();		DebugInfoAra.add("");	}	
	
	//set up window structures
	protected void initWins(int _numWins, String[] _winTtls, String[] _winDescs) {
		numDispWins = _numWins;	//must be set here!
		winRectDimOpen = new float[numDispWins][];
		winRectDimClose = new float[numDispWins][];
		//idxs : 0 : canDrawInWin; 1 : canShow3dbox; 2 : canMoveView; 3 : dispWinIs3d
		dispWinFlags = new boolean[numDispWins][numDispWinBoolFlags];
		//need 1 per display window
		winTitles = _winTtls;
		winDescr = _winDescs;
		winFillClrs = new int[numDispWins][4];
		winStrkClrs = new int[numDispWins][4];
		winTrajFillClrs = new int[numDispWins];		//set to color constants for each window
		winTrajStrkClrs = new int[numDispWins];		//set to color constants for each window
		//display window initialization
		dispWinFrames = new myDispWindow[numDispWins];			
	}//initWins
	
	//specify windows that cannot be shown simultaneously here
	protected void initXORWins(int[] _winFlags, int[] _winIdxs) {
		winFlagsXOR = _winFlags;
		//specify windows that cannot be shown simultaneously here
		winDispIdxXOR = _winIdxs;		
	}
	
	//initialize menu window
	protected void buildInitMenuWin(int _showUIMenuIDX) {
		//init sidebar menu vals
		for(int i=0;i<dispWinFlags[dispMenuIDX].length;++i) {dispWinFlags[dispMenuIDX][i] = false;}
		//set up dims for menu
		winRectDimOpen[dispMenuIDX] =  new float[]{0,0, menuWidth, height};
		winRectDimClose[dispMenuIDX] =  new float[]{0,0, hideWinWidth, height};
		
		winFillClrs[dispMenuIDX] = new int[]{255,255,255,255};
		winStrkClrs[dispMenuIDX] = new int[]{0,0,0,255};
		
		winTrajFillClrs[dispMenuIDX] = gui_Black;		//set to color constants for each window
		winTrajStrkClrs[dispMenuIDX] = gui_Black;		//set to color constants for each window		
		winTitles[dispMenuIDX] = "UI Window";
		winDescr[dispMenuIDX] = "User Controls";
		
	}//setIniMenuWin
	
	//call once for each display window before calling constructor
	protected void setInitDispWinVals(int _winIDX, float[] _dimOpen, float[] _dimClosed, boolean[] _dispFlags, int[] _fill, int[] _strk, int _trajFill, int _trajStrk) {
		winRectDimOpen[_winIDX] = _dimOpen;
		winRectDimClose[_winIDX] = _dimClosed;
		//idxs : 0 : canDrawInWin; 1 : canShow3dbox; 2 : canMoveView; 3 : dispWinIs3d
		dispWinFlags[_winIDX] = _dispFlags;
		winFillClrs[_winIDX] = _fill;
		winStrkClrs[_winIDX] = _strk;
		winTrajFillClrs[_winIDX] = _trajFill;		//set to color constants for each window
		winTrajStrkClrs[_winIDX] = _trajStrk;		//set to color constants for each window		
	}//setInitDispWinVals
	
//	
//			dispCanDrawInWinIDX 	= 0,
//			dispCanShow3dboxIDX 	= 1,
//			dispCanMoveViewIDX 		= 2,
//			dispWinIs3dIDX 			= 3;
		
	protected void finalDispWinInit() {
		for(int i =0; i < numDispWins; ++i){
			int scIdx = dispWinFlags[i][dispWinIs3dIDX] ? 1 : 0;//whether or not is 3d
			dispWinFrames[i].finalInit(dispWinFlags[i][dispWinIs3dIDX], dispWinFlags[i][dispCanMoveViewIDX], sceneCtrValsBase[scIdx], sceneFcsValsBase[scIdx]);
			dispWinFrames[i].setTrajColors(winTrajFillClrs[i], winTrajStrkClrs[i]);
			dispWinFrames[i].setRtSideUIBoxClrs(new int[]{0,0,0,200},new int[]{255,255,255,255});
		}	

	}//finalDispWinInit
	
	
	///////////////////////////////////////////
	// draw routines
	
	protected abstract void setBkgrnd();
	
	protected boolean isShowingWindow(int i){return getVisFlag(i);}//showUIMenu is first flag of window showing flags, visFlags are defined in instancing class
	
	//get difference between frames and set both glbl times
	protected final float getModAmtMillis() {
		glblStartSimFrameTime = millis();
		float modAmtMillis = (glblStartSimFrameTime - glblLastSimFrameTime);
		glblLastSimFrameTime = millis();
		return modAmtMillis;
	}
	
	//main draw loop
	protected void _drawPriv(){	
		if(!isFinalInitDone()) {initOnce(); return;}	
		float modAmtMillis = getModAmtMillis();
		//simulation section
		if(isRunSim() ){
			//run simulation
			drawCount++;									//needed here to stop draw update so that pausing sim retains animation positions	
			for(int i =1; i<numDispWins; ++i){if((isShowingWindow(i)) && (dispWinFrames[i].getFlags(myDispWindow.isRunnable))){dispWinFrames[i].simulate(modAmtMillis);}}
			if(isSingleStep()){setSimIsRunning(false);}
			simCycles++;
		}		//play in current window

		//drawing section
		pushMatrix();pushStyle();
		drawSetup();																//initialize camera, lights and scene orientation and set up eye movement		
		if((curFocusWin == -1) || (curDispWinIs3D())){	//allow for single window to have focus, but display multiple windows	
			//if refreshing screen, this clears screen, sets background
			setBkgrnd();				
			draw3D_solve3D(modAmtMillis);
			c.buildCanvas();			
			if(curDispWinCanShow3dbox()){drawBoxBnds();}
			if(dispWinFrames[curFocusWin].chkDrawMseRet()){			c.drawMseEdge();	}			
			popStyle();popMatrix(); 
		} else {	//either/or 2d window
			//2d windows paint window box so background is always cleared
			c.buildCanvas();
			c.drawMseEdge();
			popStyle();popMatrix(); 
			for(int i =1; i<numDispWins; ++i){if (isShowingWindow(i) && !(dispWinFrames[i].getFlags(myDispWindow.is3DWin))){dispWinFrames[i].draw2D(modAmtMillis);}}
		}
		drawUI(modAmtMillis);																	//draw UI overlay on top of rendered results			
		if (doSaveAnim()) {	savePic();}
		updateConsoleStrs();
	}//draw	
	
	protected void draw3D_solve3D(float modAmtMillis){
		//System.out.println("drawSolve");
		pushMatrix();pushStyle();
		for(int i =1; i<numDispWins; ++i){
			if((isShowingWindow(i)) && (dispWinFrames[i].getFlags(myDispWindow.is3DWin))){	dispWinFrames[i].draw3D(modAmtMillis);}
		}
		popStyle();popMatrix();
		//fixed xyz rgb axes for visualisation purposes and to show movement and location in otherwise empty scene
		drawAxes(100,3, new myPoint(-c.getViewDimW()/2.0f+40,0.0f,0.0f), 200, false); 		
	}//draw3D_solve3D
	
	public final void drawUI(float modAmtMillis){					
		//for(int i =1; i<numDispWins; ++i){if ( !(dispWinFrames[i].dispFlags[myDispWindow.is3DWin])){dispWinFrames[i].draw(sceneCtrVals[sceneIDX]);}}
		//dispWinFrames[0].draw(sceneCtrVals[sceneIDX]);
		for(int i =1; i<numDispWins; ++i){dispWinFrames[i].drawHeader(modAmtMillis);}
		//menu always idx 0
		dispWinFrames[0].draw2D(modAmtMillis);
		dispWinFrames[0].drawHeader(modAmtMillis);
		drawOnScreenData();				//debug and on-screen data
	}//drawUI	
	
	public final void drawAxes(double len, float stW, myPoint ctr, int alpha, boolean centered){//axes using current global orientation
		pushMatrix();pushStyle();
			strokeWeight(stW);
			stroke(255,0,0,alpha);
			if(centered){
				double off = len*.5f;
				line(ctr.x-off,ctr.y,ctr.z,ctr.x+off,ctr.y,ctr.z);stroke(0,255,0,alpha);line(ctr.x,ctr.y-off,ctr.z,ctr.x,ctr.y+off,ctr.z);stroke(0,0,255,alpha);line(ctr.x,ctr.y,ctr.z-off,ctr.x,ctr.y,ctr.z+off);} 
			else {		line(ctr.x,ctr.y,ctr.z,ctr.x+len,ctr.y,ctr.z);stroke(0,255,0,alpha);line(ctr.x,ctr.y,ctr.z,ctr.x,ctr.y+len,ctr.z);stroke(0,0,255,alpha);line(ctr.x,ctr.y,ctr.z,ctr.x,ctr.y,ctr.z+len);}
		popStyle();	popMatrix();	
	}//	drawAxes
	public final void drawAxes(double len, float stW, myPoint ctr, myVector[] _axis, int alpha, boolean drawVerts){//RGB -> XYZ axes
		pushMatrix();pushStyle();
		if(drawVerts){
			show(ctr,3,gui_Black,gui_Black, false);
			for(int i=0;i<_axis.length;++i){show(myPoint._add(ctr, myVector._mult(_axis[i],len)),3,rgbClrs[i],rgbClrs[i], false);}
		}
		strokeWeight(stW);
		for(int i =0; i<3;++i){	setColorValStroke(rgbClrs[i]);	showVec(ctr,len, _axis[i]);	}
		popStyle();	popMatrix();	
	}//	drawAxes
	public final void drawAxes(double len, float stW, myPoint ctr, myVector[] _axis, int[] clr, boolean drawVerts){//all axes same color
		pushMatrix();pushStyle();
			if(drawVerts){
				show(ctr,2,gui_Black,gui_Black, false);
				for(int i=0;i<_axis.length;++i){show(myPoint._add(ctr, myVector._mult(_axis[i],len)),2,rgbClrs[i],rgbClrs[i], false);}
			}
			strokeWeight(stW);stroke(clr[0],clr[1],clr[2],clr[3]);
			for(int i =0; i<3;++i){	showVec(ctr,len, _axis[i]);	}
		popStyle();	popMatrix();	
	}//	drawAxes
	
	public final void drawText(String str, double x, double y, double z, int clr){
		int[] c = getClr(clr);
		pushMatrix();	pushStyle();
			fill(c[0],c[1],c[2],c[3]);
			unSetCamOrient();
			translate((float)x,(float)y,(float)z);
			text(str,0,0,0);		
		popStyle();	popMatrix();	
	}//drawText	
	
	public final int addInfoStr(String str){return addInfoStr(DebugInfoAra.size(), str);}
	public final int addInfoStr(int idx, String str){	
		int lstIdx = DebugInfoAra.size();
		if(idx >= lstIdx){		for(int i = lstIdx; i <= idx; ++i){	DebugInfoAra.add(i,"");	}}
		setInfoStr(idx,str);	return idx;
	}
	public final void setInfoStr(int idx, String str){DebugInfoAra.set(idx,str);	}
	public final void drawInfoStr(float sc, int clr){drawInfoStr(sc, getClr(clr,255));}
	public final void drawInfoStr(float sc, int[] fillClr){//draw text on main part of screen
		pushMatrix();		pushStyle();
			fill(fillClr[0],fillClr[1],fillClr[2],fillClr[3]);
			translate((menuWidth),0);
			scale(sc,sc);
			for(int i = 0; i < DebugInfoAra.size(); ++i){		text((getBaseFlag(debugMode)?(i<10?"0":"")+i+":     " : "") +"     "+DebugInfoAra.get(i)+"\n\n",0,(10+(12*i)));	}
		popStyle();	popMatrix();
	}		
	
	
	//vector and point functions to be compatible with earlier code from jarek's class or previous projects	
	//draw bounding box for 3d
	public final void drawBoxBnds(){
		pushMatrix();	pushStyle();
		strokeWeight(3f);
		noFill();
		setColorValStroke(gui_TransGray);		
		box(gridDimX,gridDimY,gridDimZ);
		popStyle();	popMatrix();
	}		
	//drawsInitial setup for each draw
	public final void drawSetup(){
		perspective(PI/3.0f, (1.0f*width)/(1.0f*height), .5f, camVals[2]*100.0f);
		lights(); 	
	    dispWinFrames[curFocusWin].drawSetupWin(camVals);
	}//drawSetup		
	

	//called by sidebar menu to display current window's UI components
	public final void drawWindowGuiObjs(){
		if(curFocusWin != -1){
			pushMatrix();pushStyle();
			dispWinFrames[curFocusWin].drawGUIObjs();					//draw what user-modifiable fields are currently available
			dispWinFrames[curFocusWin].drawClickableBooleans();					//draw what user-modifiable fields are currently available
			dispWinFrames[curFocusWin].drawCustMenuObjs();					//customizable menu objects for each window
			//also launch custom function here
			dispWinFrames[curFocusWin].checkCustMenuUIObjs();			
			popStyle();	popMatrix();	
		}
	}//
	
	//use current display window's fill color for text color
	private void drawOnScreenData(){
		if(isDebugMode()){
			pushMatrix();pushStyle();			
			reInitInfoStr();
			addInfoStr(0,"mse loc on screen : " + new myPoint(mouseX, mouseY,0) + " mse loc in world :"+c.mseLoc +"  Eye loc in world :"+ c.eyeInWorld+ dispWinFrames[curFocusWin].getCamDisp());//" camera rx :  " + rx + " ry : " + ry + " dz : " + dz);
			String[] res = ((BaseBarMenu)dispWinFrames[dispMenuIDX]).getDebugData();		//get debug data for each UI object
			int numToPrint = min(res.length,80);
			for(int s=0;s<numToPrint;++s) {	addInfoStr(res[s]);}				//add info to string to be displayed for debug
			drawInfoStr(1.0f, dispWinFrames[curFocusWin].strkClr); 	
			popStyle();	popMatrix();		
		}
		else if(showInfo){
			pushMatrix();pushStyle();			
			reInitInfoStr();	
			String[] res = consoleStrings.toArray(new String[0]);
			int dispNum = min(res.length, 80);
			for(int i=0;i<dispNum;++i){addInfoStr(res[i]);}
		    drawInfoStr(1.1f, dispWinFrames[curFocusWin].strkClr); 
			popStyle();	popMatrix();	
		}
	}//drawOnScreenData
	
	protected void updateConsoleStrs(){
		++drawCount;
		if(drawCount % cnslStrDecay == 0){drawCount = 0;	consoleStrings.poll();}			
	}//updateConsoleStrs
	
	//////////////////////////////////
	// end draw routines

		
	public abstract void initVisFlags();
	public abstract void setVisFlag(int idx, boolean val);
	//this will not execute the code in setVisFlag, which might cause a loop
	public abstract void forceVisFlag(int idx, boolean val);
	public abstract boolean getVisFlag(int idx);
	
	//base class flags init
	private void initBaseFlags(){baseFlags = new int[1 + numBaseFlags/32];for(int i =0; i<numBaseFlags;++i){forceBaseFlag(i,false);}}		
	//set baseclass flags  //setBaseFlag(showIDX, 
	public final void setBaseFlag(int idx, boolean val){
		int flIDX = idx/32, mask = 1<<(idx%32);
		baseFlags[flIDX] = (val ?  baseFlags[flIDX] | mask : baseFlags[flIDX] & ~mask);
		switch(idx){
			case debugMode 			: { break;}//anything special for debugMode 	
			case finalInitDone		: { break;}//flag to handle long setup - processing seems to time out if setup takes too long, so this will continue setup in the first draw loop
			case saveAnim 			: { break;}//anything special for saveAnim 			
			case altKeyPressed 		: { break;}//anything special for altKeyPressed 	
			case shiftKeyPressed 	: { break;}//anything special for shiftKeyPressed 	
			case cntlKeyPressed		: { break;}
			case mouseClicked 		: { break;}//anything special for mouseClicked 		
			case modView	 		: { break;}//anything special for modView	 	
			case drawing			: { break;}
			case runSim				: { break;}// handleTrnsprt((val ? 2 : 1) ,(val ? 1 : 0),false); break;}		//anything special for runSim	
			case showRtSideMenu		: {	for(int i =1; i<dispWinFrames.length;++i){dispWinFrames[i].setRtSideInfoWinSt(val);}break;}	//set value for every window - to show or not to show info window
			case flipDrawnTraj		: { for(int i =1; i<dispWinFrames.length;++i){dispWinFrames[i].rebuildAllDrawnTrajs();}break;}						//whether or not to flip the drawn melody trajectory, width-wise
			case singleStep 		: { break;}
		}				
	}//setBaseFlag
	//force base flag - bypass any window setting
	private void forceBaseFlag(int idx, boolean val) {		
		int flIDX = idx/32, mask = 1<<(idx%32);
		baseFlags[flIDX] = (val ?  baseFlags[flIDX] | mask : baseFlags[flIDX] & ~mask);
	}
	//get baseclass flag
	public final boolean getBaseFlag(int idx){int bitLoc = 1<<(idx%32);return (baseFlags[idx/32] & bitLoc) == bitLoc;}	
	public final void clearBaseFlags(int[] idxs){		for(int idx : idxs){setBaseFlag(idx,false);}	}			

	public final boolean isDebugMode() {return getBaseFlag(debugMode);}
	
	public final boolean isFinalInitDone() {return getBaseFlag(finalInitDone);}
	public final boolean isRunSim() {return getBaseFlag(runSim);}
	public final boolean isSingleStep() {return getBaseFlag(singleStep);}
	public final boolean doSaveAnim() {return getBaseFlag(saveAnim);}
	public final boolean doFlipTraj() {return getBaseFlag(flipDrawnTraj);}
	public final boolean doShowRtSideMenu() {return getBaseFlag(showRtSideMenu);}	
	
	public final boolean shiftIsPressed() {return getBaseFlag(shiftKeyPressed);}
	public final boolean altIsPressed() {return getBaseFlag(altKeyPressed);}
	public final boolean cntlIsPressed() {return getBaseFlag(cntlKeyPressed);}
	public final boolean mouseIsClicked() {return getBaseFlag(mouseClicked);}
	public final boolean IsModView() {return getBaseFlag(modView);}
	public final boolean IsDrawing() {return getBaseFlag(drawing);}
	
	//display window flags
	public final boolean dispWinCanDrawInWin(int wIdx) {return dispWinFlags[wIdx][dispCanDrawInWinIDX];}
	public final boolean dispWinCanShow3dbox(int wIdx) {return dispWinFlags[wIdx][dispCanShow3dboxIDX];}
	public final boolean dispWinCanMoveView(int wIdx) {return dispWinFlags[wIdx][dispCanMoveViewIDX];}
	public final boolean dispWinIs3D(int wIdx) {return dispWinFlags[wIdx][dispWinIs3dIDX];}
	
	public final boolean curDispWinCanDrawInWin() {return dispWinFlags[curFocusWin][dispCanDrawInWinIDX];}
	public final boolean curDispWinCanShow3dbox() {return dispWinFlags[curFocusWin][dispCanShow3dboxIDX];}
	public final boolean curDispWinCanMoveView() {return dispWinFlags[curFocusWin][dispCanMoveViewIDX];}
	public final boolean curDispWinIs3D() {return dispWinFlags[curFocusWin][dispWinIs3dIDX];}
	
	public final void setSimIsRunning(boolean val) {setBaseFlag(runSim,val);}
	public final void toggleSimIsRunning() {setBaseFlag(runSim, !getBaseFlag(runSim));}
	public final void setSimIsSingleStep(boolean val) {setBaseFlag(singleStep,val);}
	public final void setShowRtSideMenu(boolean val) {setBaseFlag(showRtSideMenu,val);}
	
	public final void setShiftPressed(boolean val) {setBaseFlag(shiftKeyPressed,val);}
	public final void setAltPressed(boolean val) {setBaseFlag(altKeyPressed,val);}
	public final void setCntlPressed(boolean val) {setBaseFlag(cntlKeyPressed,val);}
	public final void setMouseClicked(boolean val) {setBaseFlag(mouseClicked,val);}
	public final void setModView(boolean val) {setBaseFlag(modView,val);}
	public final void setIsDrawing(boolean val) {setBaseFlag(drawing,val);}
	public final void setFinalInitDone(boolean val) {setBaseFlag(finalInitDone, val);}	
	public final void setSaveAnim(boolean val) {setBaseFlag(saveAnim, val);}
	public final void toggleSaveAnim() {setBaseFlag(saveAnim, !getBaseFlag(saveAnim));}
	
	public final void keyReleased(){
		if(key==CODED) {
			if((getBaseFlag(shiftKeyPressed)) && (keyCode == 16)){endShiftKey();}
			if((getBaseFlag(cntlKeyPressed)) && (keyCode == 17)){endCntlKey();}
			if((getBaseFlag(altKeyPressed)) && (keyCode == 18)){endAltKey();}
		}
	}		
	//modview tied to shift key
	private void endShiftKey(){	clearBaseFlags(new int []{shiftKeyPressed, modView});	for(int i =0; i<numDispWins; ++i){dispWinFrames[i].endShiftKey();}}
	private void endAltKey(){	clearBaseFlags(new int []{altKeyPressed});				for(int i =0; i<numDispWins; ++i){dispWinFrames[i].endAltKey();}}
	private void endCntlKey(){	clearBaseFlags(new int []{cntlKeyPressed});				for(int i =0; i<numDispWins; ++i){dispWinFrames[i].endCntlKey();}}
	
	public abstract double clickValModMult();
	public abstract boolean isClickModUIVal();

	public final void mouseMoved(){for(int i =0; i<numDispWins; ++i){if (dispWinFrames[i].handleMouseMove(mouseX, mouseY)){return;}}}
	public final void mousePressed() {
		setBaseFlag(mouseClicked, true);
		if(mouseButton == LEFT){			myMouseClicked(0);} 
		else if (mouseButton == RIGHT) {	myMouseClicked(1);}
		//for(int i =0; i<numDispWins; ++i){	if (dispWinFrames[i].handleMouseClick(mouseX, mouseY,c.getMseLoc(sceneCtrVals[sceneIDX]))){	return;}}
	}// mousepressed		
	private void myMouseClicked(int mseBtn){ 	for(int i =0; i<numDispWins; ++i){if (dispWinFrames[i].handleMouseClick(mouseX, mouseY,mseBtn)){return;}}}
	
	public final void mouseDragged(){
		if(mouseButton == LEFT){			myMouseDragged(0);}
		else if (mouseButton == RIGHT) {	myMouseDragged(1);}
	}//mouseDragged()
	private void myMouseDragged(int mseBtn){	for(int i =0; i<numDispWins; ++i){if (dispWinFrames[i].handleMouseDrag(mouseX, mouseY, pmouseX, pmouseY,c.getMseDragVec(),mseBtn)) {return;}}}
	
	//only for zooming
	public final void mouseWheel(MouseEvent event) {
		if (dispWinFrames[curFocusWin].getFlags(myDispWindow.canChgView)) {// (canMoveView[curFocusWin]){	
			float mult = (getBaseFlag(shiftKeyPressed)) ? 5.0f * mouseWhlSens : mouseWhlSens;
			dispWinFrames[curFocusWin].handleViewChange(true,(mult * event.getCount()),0);
		}
	}

	public final void mouseReleased(){
		clearBaseFlags(new int[]{mouseClicked, modView});
		for(int i =0; i<numDispWins; ++i){dispWinFrames[i].handleMouseRelease();}
		setBaseFlag(drawing, false);
		//c.clearMsDepth();
	}//mouseReleased
	
	//set the height of each window that is above the popup window, to move up or down when it changes size
	public final void setWinsHeight(int popUpWinIDX){
		//skip first window - ui menu
		for(int i =0;i<winDispIdxXOR.length;++i){		dispWinFrames[winDispIdxXOR[i]].setRectDimsY( dispWinFrames[popUpWinIDX].getRectDim(1));	}						
	}
	
	public final void setWinFlagsXOR(int idx, boolean val){
		//outStr2Scr("SetWinFlagsXOR : idx " + idx + " val : " + val);
		if(val){//turning one on
			//turn off not shown, turn on shown				
			for(int i =0;i<winDispIdxXOR.length;++i){//check windows that should be mutually exclusive during display
				if(winDispIdxXOR[i]!= idx){dispWinFrames[winDispIdxXOR[i]].setShow(false);handleShowWin(i ,0,false); forceVisFlag(winFlagsXOR[i], false);}
				else {
					dispWinFrames[idx].setShow(true);
					handleShowWin(i ,1,false); 
					forceVisFlag(winFlagsXOR[i], true);
					curFocusWin = winDispIdxXOR[i];
					//setCamView();	//camera now handled by individual windows
					dispWinFrames[idx].setInitCamView();
				}
			}
		} else {//if turning off a window - need a default uncloseable window - for now just turn on next window
			//idx is dispXXXIDX idx of allowable windows (1+ since idx 0 is sidebar menu), so use idx-1 for mod function
			//add 1 to (idx-1) to get next window index, modulo for range adherence, and then add 1 to move back to 1+ from 0+ result from mod		
			//setWinFlagsXOR((((idx-1) + 1) % winFlagsXOR.length)+1, true);
			setWinFlagsXOR((idx % winFlagsXOR.length)+1, true);
		}			
	}//setWinFlagsXOR

	//get the ui rect values of the "master" ui region (another window) -> this is so ui objects of one window can be made, clicked, and shown displaced from those of the parent windwo
	public abstract float[] getUIRectVals(int idx);
	
	//clear menu side bar buttons when window-specific processing is finished
	//isSlowProc means original calling process lasted longer than mouse click release and so button state should be forced to be off
	public final void clearBtnState(int _type, int col, boolean isSlowProc) {
		int row = _type;
		BaseBarMenu win = (BaseBarMenu)dispWinFrames[dispMenuIDX];
		win.guiBtnWaitForProc[row][col] = false;
		if(isSlowProc) {win.guiBtnSt[row][col] = 0;}		
	}//clearBtnState 
	
	public final void setAllMenuBtnNames(String[][] btnNames) {
		for(int _type = 0;_type<btnNames.length;++_type) {((BaseBarMenu)dispWinFrames[dispMenuIDX]).setAllBtnNames(_type,btnNames[_type]);}
	}
	
	//these tie using the UI buttons to modify the window in with using the boolean tags - PITA but currently necessary
	public final void handleShowWin(int btn, int val){handleShowWin(btn, val, true);}					//display specific windows - multi-select/ always on if sel
	//these tie using the UI buttons to modify the window in with using the boolean tags - PITA but currently necessary
	public abstract void handleShowWin(int btn, int val, boolean callFlags);
	//process to handle file io	- TODO	
	public final void handleFileCmd(int _type, int btn, int val){handleFileCmd(_type,btn, val, true);}					//display specific windows - multi-select/ always on if sel
	public final void handleFileCmd(int _type, int btn, int val, boolean callFlags){//{"Load","Save"},							//load an existing score, save an existing score - momentary	
		if(!callFlags){			setMenuBtnState(_type,btn, val);		}  else {
			switch(btn){
				case 0 : {selectInput("Select a file to load from : ", "loadFromFile", currFileIOLoc);break;}
				case 1 : {selectOutput("Select a file to save to : ", "saveToFile", currFileIOLoc);break;}
			}
			((BaseBarMenu)dispWinFrames[dispMenuIDX]).hndlMouseRelIndiv();
		}
	}//handleFileCmd
	//turn off specific function button that might have been kept on during processing - btn must be in range of size of guiBtnSt[mySideBarMenu.btnAuxFuncIdx]
	//isSlowProc means function this was waiting on is a slow process and escaped the click release in the window (i.e. if isSlowProc then we must force button to be off)
	//public final void clearFuncBtnSt(int btn, boolean isSlowProc) {clearBtnState(mySideBarMenu.btnAuxFuncIdx,btn, isSlowProc);}

	public final void handleMenuBtnSelCmp(int _type, int btn, int val){handleMenuBtnSelCmp(_type, btn, val, true);}					//display specific windows - multi-select/ always on if sel
	public final void handleMenuBtnSelCmp(int _type, int btn, int val, boolean callFlags){
		if(!callFlags){			setMenuBtnState(_type,btn, val);		} 
		else {					dispWinFrames[curFocusWin].clickSideMenuBtn(_type, btn);		}
	}//handleAddDelSelCmp	
	
	
	protected void setMenuBtnState(int row, int col, int val) {
		((BaseBarMenu)dispWinFrames[dispMenuIDX]).guiBtnSt[row][col] = val;	
		if (val == 1) {
			outStr2Scr("turning on button row : " + row + "  col " + col);
			((BaseBarMenu)dispWinFrames[dispMenuIDX]).setWaitForProc(row,col);}//if programmatically (not through UI) setting button on, then set wait for proc value true 
	}//setMenuBtnState	
	
	public void loadFromFile(File file){
		if (file == null) {
		    outStr2Scr("Load was cancelled.");
		    return;
		} 		
		//reset to match navigation in file IO window
		currFileIOLoc = file;
		dispWinFrames[curFocusWin].loadFromFile(file);
	
	}//loadFromFile
	
	public void saveToFile(File file){
		if (file == null) {
		    outStr2Scr("Save was cancelled.");
		    return;
		} 
		//reset to match navigation in file IO window
		currFileIOLoc = file;
		dispWinFrames[curFocusWin].saveToFile(file);
	}//saveToFile	
	
	
	//2d range checking of point
	public final boolean ptInRange(double x, double y, double minX, double minY, double maxX, double maxY){return ((x > minX)&&(x <= maxX)&&(y > minY)&&(y <= maxY));}	

	public final void setCamOrient(){rotateX(rx);rotateY(ry); rotateX(PI/(2.0f));		}//sets the rx, ry, pi/2 orientation of the camera eye	
	public final void unSetCamOrient(){rotateX(-PI/(2.0f)); rotateY(-ry);   rotateX(-rx); }//reverses the rx,ry,pi/2 orientation of the camera eye - paints on screen and is unaffected by camera movement

	//save screenshot
	public final void savePic(){	
		//if(!flags[this.runSim]) {return;}//don't save until actually running simulation
		//idx 0 is directory, idx 1 is file name prefix
		String[] ssName = dispWinFrames[curFocusWin].getSaveFileDirName();
		//save(screenShotPath + prjNmShrt + ((animCounter < 10) ? "0000" : ((animCounter < 100) ? "000" : ((animCounter < 1000) ? "00" : ((animCounter < 10000) ? "0" : "")))) + animCounter + ".jpg");		
		String saveDirAndSubDir = ssName[0] + //"run_"+String.format("%02d", runCounter)  + 
				ssName[1] + File.separatorChar;		
		save(saveDirAndSubDir + String.format("%06d", animCounter) + ".jpg");		
		animCounter++;		
	}
	
	public final void line(double x1, double y1, double z1, double x2, double y2, double z2){line((float)x1,(float)y1,(float)z1,(float)x2,(float)y2,(float)z2 );}
	public final void line(myPoint p1, myPoint p2){line((float)p1.x,(float)p1.y,(float)p1.z,(float)p2.x,(float)p2.y,(float)p2.z);}
	public final void line(myPointf p1, myPointf p2){line(p1.x,p1.y,p1.z,p2.x,p2.y,p2.z);}
	//print out multiple-line text to screen
	public final void ml_text(String str, float x, float y){
		String[] res = str.split("\\r?\\n");
		float disp = 0;
		for(int i =0; i<res.length; ++i){
			text(res[i],x, y+disp);		//add console string output to screen display- decays over time
			disp += 12;
		}
	}
	//print out a string ara with perLine # of strings per line
	public final void outStr2ScrAra(String[] sAra, int perLine){
		for(int i=0;i<sAra.length; i+=perLine){
			String s = "";
			for(int j=0; j<perLine; ++j){	s+= sAra[i+j]+ "\t";}
			outStr2Scr(s,true);}
	}
	//print out string in display window
	public final void outStr2Scr(String str){outStr2Scr(str,true);}
	//print informational string data to console, and to screen
	public final void outStr2Scr(String str, boolean showDraw){
		if(trim(str) != ""){	System.out.println(str);}
		String[] res = str.split("\\r?\\n");
		if(showDraw){
			for(int i =0; i<res.length; ++i){
				consoleStrings.add(res[i]);		//add console string output to screen display- decays over time
			}
		}
	}
	
	public String getScreenShotSaveName(String prjNmShrt) {
		return sketchPath() +File.separatorChar+prjNmShrt+"_"+getDateString()+File.separatorChar+prjNmShrt+"_img"+getTimeString() + ".jpg";
	}
	
	//build a date with each component separated by token
	public String getDateTimeString(){return getDateTimeString(true, false,".");}
	public String getDateTimeString(boolean useYear, boolean toSecond, String token){
		String result = "";
		int val;
		if(useYear){val = now.get(Calendar.YEAR);		result += ""+val+token;}
		val = now.get(Calendar.MONTH)+1;				result += (val < 10 ? "0"+val : ""+val)+ token;
		val = now.get(Calendar.DAY_OF_MONTH);			result += (val < 10 ? "0"+val : ""+val)+ token;
		val = now.get(Calendar.HOUR_OF_DAY);					result += (val < 10 ? "0"+val : ""+val)+ token;
		val = now.get(Calendar.MINUTE);					result += (val < 10 ? "0"+val : ""+val);
		if(toSecond){val = now.get(Calendar.SECOND);	result += token + (val < 10 ? "0"+val : ""+val);}
		return result;
	}
	//utilities
	public String getDateString(){return getDateString(true, "-");}
	public String getDateString(boolean useYear, String token){
		String result = "";
		int val;
		if(useYear){val = now.get(Calendar.YEAR);		result += ""+val+token;}
		val = now.get(Calendar.MONTH)+1;				result += (val < 10 ? "0"+val : ""+val)+ token;
		val = now.get(Calendar.DAY_OF_MONTH);			result += (val < 10 ? "0"+val : ""+val)+ token;
		return result;
	}//getDateString
	
	public String getTimeString(){return getTimeString(true, "-");}
	public String getTimeString(boolean toSecond, String token){
		String result = "";
		int val;
		val = now.get(Calendar.HOUR_OF_DAY);					result += (val < 10 ? "0"+val : ""+val)+ token;
		val = now.get(Calendar.MINUTE);					result += (val < 10 ? "0"+val : ""+val);
		if(toSecond){val = now.get(Calendar.SECOND);	result += token + (val < 10 ? "0"+val : ""+val);}
		return result;
	}//getDateString
	
	
	//handle user-driven file load or save - returns a filename + filepath string
	public String FileSelected(File selection){
		if (null==selection){return null;}
		return selection.getAbsolutePath();		
	}//FileSelected
	

	public String getFName(String fNameAndPath){
		String[] strs = fNameAndPath.split("/");
		return strs[strs.length-1];
	}
	
	//load a file as text strings
	public String[] loadFileIntoStringAra(String fileName, String dispYesStr, String dispNoStr){
		String[] strs = null;
		try{
			strs = loadStrings(fileName);
			System.out.println(dispYesStr+"\tLength : " + strs.length);
		} catch (Exception e){System.out.println("!!"+dispNoStr);return null;}
		return strs;		
	}//loadFileIntoStrings
	
	//public final void scribeHeaderRight(String s) {scribeHeaderRight(s, 20);} // writes black on screen top, right-aligned
	//public final void scribeHeaderRight(String s, float y) {fill(0); text(s,width-6*s.length(),y); noFill();} // writes black on screen top, right-aligned
	public final void displayHeader() { // Displays title and authors face on screen
	    float stVal = 17;
	    int idx = 1;	
	    translate(0,10,0);
	    fill(0); text("Shift-Click-Drag to change view.",width-190, stVal*idx++); noFill(); 
	    fill(0); text("Shift-RClick-Drag to zoom.",width-160, stVal*idx++); noFill();
	    fill(0); text("John Turner",width-75, stVal*idx++); noFill();	
	    }
	
	//project passed point onto box surface based on location - to help visualize the location in 3d
	public final void drawProjOnBox(myPoint p){
		//myPoint[]  projOnPlanes = new myPoint[6];
		myPoint prjOnPlane;
		//public final myPoint intersectPl(myPoint E, myVector T, myPoint A, myPoint B, myPoint C) { // if ray from E along T intersects triangle (A,B,C), return true and set proposal to the intersection point
		pushMatrix();
		translate(-p.x,-p.y,-p.z);
		for(int i  = 0; i< 6; ++i){				
			prjOnPlane = bndChkInCntrdBox3D(intersectPl(p, boxNorms[i], boxWallPts[i][0],boxWallPts[i][1],boxWallPts[i][2]));				
			show(prjOnPlane,5,rgbClrs[i/2],rgbClrs[i/2], false);				
		}
		popMatrix();
	}//drawProjOnBox
	private static final double third = 1.0/3.0;
	public final myVectorf getRandPosInSphere(double rad, myVectorf ctr){
		myVectorf pos = new myVectorf();
		do{
			double u = ThreadLocalRandom.current().nextDouble(0,1), r = rad * Math.pow(u, third),
					cosTheta = ThreadLocalRandom.current().nextDouble(-1,1), sinTheta =  Math.sin(Math.acos(cosTheta)),
					phi = ThreadLocalRandom.current().nextDouble(0,PConstants.TWO_PI);
			pos.set(sinTheta * Math.cos(phi), sinTheta * Math.sin(phi),cosTheta);
			pos._mult(r);
			pos._add(ctr);
		} while (pos.z < 0);
		return pos;
	}
	
	public final myVectorf getRandPosOnSphere(double rad, myVectorf ctr){
		myVectorf pos = new myVectorf();
		//do{
			double 	cosTheta = ThreadLocalRandom.current().nextDouble(-1,1), sinTheta =  Math.sin(Math.acos(cosTheta)),
					phi = ThreadLocalRandom.current().nextDouble(0,PConstants.TWO_PI);
			pos.set(sinTheta * Math.cos(phi), sinTheta * Math.sin(phi),cosTheta);
			pos._mult(rad);
			pos._add(ctr);
		//} while (pos.z < 0);
		return pos;
	}
	
	
	//very fast mechanism for setting an array of doubles to a specific val - takes advantage of caching
	public final void dAraFill(double[] ara, double val){
		int len = ara.length;
		if (len > 0){ara[0] = val; }
		for (int i = 1; i < len; i += i){  System.arraycopy(ara, 0, ara, i, ((len - i) < i) ? (len - i) : i);  }		
	}
	
	//convert a world location within the bounded cube region to be a 4-int color array
	public final int[] getClrFromCubeLoc(float[] t){
		return new int[]{(int)(255*(t[0]-cubeBnds[0][0])/cubeBnds[1][0]),(int)(255*(t[1]-cubeBnds[0][1])/cubeBnds[1][1]),(int)(255*(t[2]-cubeBnds[0][2])/cubeBnds[1][2]),255};
	}
	
	//performs shuffle
	public String[] shuffleStrList(String[] _list, String type){
		String tmp = "";
		ThreadLocalRandom tr = ThreadLocalRandom.current();
		for(int i=(_list.length-1);i>0;--i){
			int j = (int)(tr.nextDouble(0,(i+1))) ;
			tmp = _list[i];
			_list[i] = _list[j];
			_list[j] = tmp;
		//	outStr2Scr("From i : " + i + " to j : " + j);
		}
		outStr2Scr("String list of Sphere " + type + " shuffled");
		return _list;
	}//shuffleStrList
	
	//random location within coords[0] and coords[1] extremal corners of a cube - bnds is to give a margin of possible random values
	public myVectorf getRandPosInCube(float[][] coords, float bnds){
		ThreadLocalRandom tr = ThreadLocalRandom.current();
		return new myVectorf(
				tr.nextDouble(coords[0][0]+bnds,(coords[0][0] + coords[1][0] - bnds)),
				tr.nextDouble(coords[0][1]+bnds,(coords[0][1] + coords[1][1] - bnds)),
				tr.nextDouble(coords[0][2]+bnds,(coords[0][2] + coords[1][2] - bnds)));}		
	public final myPoint getScrLocOf3dWrldPt(myPoint pt){	return new myPoint(screenX((float)pt.x,(float)pt.y,(float)pt.z),screenY((float)pt.x,(float)pt.y,(float)pt.z),screenZ((float)pt.x,(float)pt.y,(float)pt.z));}
	
	public final myPoint bndChkInBox2D(myPoint p){p.set(Math.max(0,Math.min(p.x,grid2D_X)),Math.max(0,Math.min(p.y,grid2D_Y)),0);return p;}
	public final myPoint bndChkInBox3D(myPoint p){p.set(Math.max(0,Math.min(p.x,gridDimX)), Math.max(0,Math.min(p.y,gridDimY)),Math.max(0,Math.min(p.z,gridDimZ)));return p;}	
	public final myPoint bndChkInCntrdBox3D(myPoint p){
		p.set(Math.max(-hGDimX,Math.min(p.x,hGDimX)), 
				Math.max(-hGDimY,Math.min(p.y,hGDimY)),
				Math.max(-hGDimZ,Math.min(p.z,hGDimZ)));return p;}	
	 
	public final void translate(myPoint p){translate((float)p.x,(float)p.y,(float)p.z);}
	public final void translate(myPointf p){translate(p.x,p.y,p.z);}
	public final void translate(myVector p){translate((float)p.x,(float)p.y,(float)p.z);}
	public final void translate(double x, double y, double z){translate((float)x,(float)y,(float)z);}
	public final void translate(double x, double y){translate((float)x,(float)y);}
	public final void rotate(float thet, myPoint axis){rotate(thet, (float)axis.x,(float)axis.y,(float)axis.z);}
	public final void rotate(float thet, double x, double y, double z){rotate(thet, (float)x,(float)y,(float)z);}
	//************************************************************************
	//**** SPIRAL
	//************************************************************************
	//3d rotation - rotate P by angle a around point G and axis normal to plane IJ
	public final myPoint R(myPoint P, double a, myVector I, myVector J, myPoint G) {
		double x= myVector._dot(new myVector(G,P),U(I)), y=myVector._dot(new myVector(G,P),U(J)); 
		double c=Math.cos(a), s=Math.sin(a); 
		double iXVal = x*c-x-y*s, jYVal= x*s+y*c-y;			
		return myPoint._add(P,iXVal,I,jYVal,J); }; 
		
	public cntlPt R(cntlPt P, double a, myVector I, myVector J, myPoint G) {
		double x= myVector._dot(new myVector(G,P),U(I)), y=myVector._dot(new myVector(G,P),U(J)); 
		double c=Math.cos(a), s=Math.sin(a); 
		double iXVal = x*c-x-y*s, jYVal= x*s+y*c-y;		
		return new cntlPt(this, P(P,iXVal,I,jYVal,J), P.r, P.w); };
		
	public final myPoint PtOnSpiral(myPoint A, myPoint B, myPoint C, double t) {
		//center is coplanar to A and B, and coplanar to B and C, but not necessarily coplanar to A, B and C
		//so center will be coplanar to mp(A,B) and mp(B,C) - use mpCA midpoint to determine plane mpAB-mpBC plane?
		myPoint mAB = new myPoint(A,.5f, B);
		myPoint mBC = new myPoint(B,.5f, C);
		myPoint mCA = new myPoint(C,.5f, A);
		myVector mI = U(mCA,mAB);
		myVector mTmp = myVector._cross(mI,U(mCA,mBC));
		myVector mJ = U(mTmp._cross(mI));	//I and J are orthonormal
		double a =spiralAngle(A,B,B,C); 
		double s =spiralScale(A,B,B,C);
		
		//myPoint G = spiralCenter(a, s, A, B, mI, mJ); 
		myPoint G = spiralCenter(A, mAB, B, mBC); 
		return new myPoint(G, Math.pow(s,t), R(A,t*a,mI,mJ,G));
	  }
	public double spiralAngle(myPoint A, myPoint B, myPoint C, myPoint D) {return myVector._angleBetween(new myVector(A,B),new myVector(C,D));}
	public double spiralScale(myPoint A, myPoint B, myPoint C, myPoint D) {return myPoint._dist(C,D)/ myPoint._dist(A,B);}
	
	public final myPoint R(myPoint Q, myPoint C, myPoint P, myPoint R) { // returns rotated version of Q by angle(CP,CR) parallel to plane (C,P,R)
		myVector I0=U(C,P), I1=U(C,R), V=new myVector(C,Q); 
		double c=myPoint._dist(I0,I1), s=Math.sqrt(1.-(c*c)); 
		if(Math.abs(s)<0.00001) return Q;
		myVector J0=V(1./s,I1,-c/s,I0);  
		myVector J1=V(-s,I0,c,J0);  
		double x=V._dot(I0), y=V._dot(J0);  
		return P(Q,x,M(I1,I0),y,M(J1,J0)); 
	} 	
	// spiral given 4 points, AB and CD are edges corresponding through rotation
	public final myPoint spiralCenter(myPoint A, myPoint B, myPoint C, myPoint D) {         // new spiral center
		myVector AB=V(A,B), CD=V(C,D), AC=V(A,C);
		double m=CD.magn/AB.magn, n=CD.magn*AB.magn;		
		myVector rotAxis = U(AB._cross(CD));		//expect ab and ac to be coplanar - this is the axis to rotate around to find f
		
		myVector rAB = myVector._rotAroundAxis(AB, rotAxis, PConstants.HALF_PI);
		double c=AB._dot(CD)/n, 
				s=rAB._dot(CD)/n;
		double AB2 = AB._dot(AB), a=AB._dot(AC)/AB2, b=rAB._dot(AC)/AB2;
		double x=(a-m*( a*c+b*s)), y=(b-m*(-a*s+b*c));
		double d=1+m*(m-2*c);  if((c!=1)&&(m!=1)) { x/=d; y/=d; };
		return P(P(A,x,AB),y,rAB);
	  }
	
	
	public final void cylinder(myPoint A, myPoint B, float r, int c1, int c2) {
		myPoint P = A;
		myVector V = V(A,B);
		myVector I = c.getDrawSNorm();//U(Normal(V));
		myVector J = U(N(I,V));
		float da = TWO_PI/36;
		beginShape(QUAD_STRIP);
			for(float a=0; a<=TWO_PI+da; a+=da) {fill(c1); gl_vertex(P(P,r*cos(a),I,r*sin(a),J,0,V)); fill(c2); gl_vertex(P(P,r*cos(a),I,r*sin(a),J,1,V));}
		endShape();
	}
	
	//point functions
	public final myPoint P() {return new myPoint(); };                                                                          // point (x,y,z)
	public final myPoint P(double x, double y, double z) {return new myPoint(x,y,z); };                                            // point (x,y,z)
	public final myPoint P(myPoint A) {return new myPoint(A.x,A.y,A.z); };                                                           // copy of point P
	public final myPoint P(myPoint A, double s, myPoint B) {return new myPoint(A.x+s*(B.x-A.x),A.y+s*(B.y-A.y),A.z+s*(B.z-A.z)); };        // A+sAB
	public final myPoint L(myPoint A, double s, myPoint B) {return new myPoint(A.x+s*(B.x-A.x),A.y+s*(B.y-A.y),A.z+s*(B.z-A.z)); };        // A+sAB
	public final myPoint P(myPoint A, myPoint B) {return P((A.x+B.x)/2.0,(A.y+B.y)/2.0,(A.z+B.z)/2.0); }                             // (A+B)/2
	public final myPoint P(myPoint A, myPoint B, myPoint C) {return new myPoint((A.x+B.x+C.x)/3.0,(A.y+B.y+C.y)/3.0,(A.z+B.z+C.z)/3.0); };     // (A+B+C)/3
	public final myPoint P(myPoint A, myPoint B, myPoint C, myPoint D) {return P(P(A,B),P(C,D)); };                                            // (A+B+C+D)/4
	public final myPoint P(double s, myPoint A) {return new myPoint(s*A.x,s*A.y,s*A.z); };                                            // sA
	public final myPoint A(myPoint A, myPoint B) {return new myPoint(A.x+B.x,A.y+B.y,A.z+B.z); };                                         // A+B
	public final myPoint P(double a, myPoint A, double b, myPoint B) {return A(P(a,A),P(b,B));}                                        // aA+bB 
	public final myPoint P(double a, myPoint A, double b, myPoint B, double c, myPoint C) {return A(P(a,A),P(b,B,c,C));}                     // aA+bB+cC 
	public final myPoint P(double a, myPoint A, double b, myPoint B, double c, myPoint C, double d, myPoint D){return A(P(a,A,b,B),P(c,C,d,D));}   // aA+bB+cC+dD
	public final myPoint P(myPoint P, myVector V) {return new myPoint(P.x + V.x, P.y + V.y, P.z + V.z); }                                 // P+V
	public final myPoint P(myPoint P, double s, myVector V) {return new myPoint(P.x+s*V.x,P.y+s*V.y,P.z+s*V.z);}                           // P+sV
	public final myPoint P(myPoint O, double x, myVector I, double y, myVector J) {return P(O.x+x*I.x+y*J.x,O.y+x*I.y+y*J.y,O.z+x*I.z+y*J.z);}  // O+xI+yJ
	public final myPoint P(myPoint O, double x, myVector I, double y, myVector J, double z, myVector K) {return P(O.x+x*I.x+y*J.x+z*K.x,O.y+x*I.y+y*J.y+z*K.y,O.z+x*I.z+y*J.z+z*K.z);}  // O+xI+yJ+kZ
	void makePts(myPoint[] C) {for(int i=0; i<C.length; i++) C[i]=P();}
	
	//draw a circle - JT
	public final void circle(myPoint P, float r, myVector I, myVector J, int n) {myPoint[] pts = new myPoint[n];pts[0] = P(P,r,U(I));float a = (2*PI)/(1.0f*n);for(int i=1;i<n;++i){pts[i] = R(pts[i-1],a,J,I,P);}pushMatrix(); pushStyle();noFill(); show(pts);popStyle();popMatrix();}; // render sphere of radius r and center P
	
	public final void circle(myPoint p, float r){ellipse((float)p.x, (float)p.y, r, r);}
	void circle(float x, float y, float r1, float r2){ellipse(x,y, r1, r2);}
	
	void noteArc(float[] dims, int[] noteClr){
		noFill();
		setStroke(noteClr);
		strokeWeight(1.5f*dims[3]);
		arc(0,0, dims[2], dims[2], dims[0] - this.HALF_PI, dims[1] - this.HALF_PI);
	}
	//draw a ring segment from alphaSt in radians to alphaEnd in radians
	void noteArc(myPoint ctr, float alphaSt, float alphaEnd, float rad, float thickness, int[] noteClr){
		noFill();
		setStroke(noteClr);
		strokeWeight(thickness);
		arc((float)ctr.x, (float)ctr.y, rad, rad, alphaSt - this.HALF_PI, alphaEnd- this.HALF_PI);
	}
	
	
	void bezier(myPoint A, myPoint B, myPoint C, myPoint D) {bezier((float)A.x,(float)A.y,(float)A.z,(float)B.x,(float)B.y,(float)B.z,(float)C.x,(float)C.y,(float)C.z,(float)D.x,(float)D.y,(float)D.z);} // draws a cubic Bezier curve with control points A, B, C, D
	void bezier(myPoint [] C) {bezier(C[0],C[1],C[2],C[3]);} // draws a cubic Bezier curve with control points A, B, C, D
	myPoint bezierPoint(myPoint[] C, float t) {return P(bezierPoint((float)C[0].x,(float)C[1].x,(float)C[2].x,(float)C[3].x,(float)t),bezierPoint((float)C[0].y,(float)C[1].y,(float)C[2].y,(float)C[3].y,(float)t),bezierPoint((float)C[0].z,(float)C[1].z,(float)C[2].z,(float)C[3].z,(float)t)); }
	myVector bezierTangent(myPoint[] C, float t) {return V(bezierTangent((float)C[0].x,(float)C[1].x,(float)C[2].x,(float)C[3].x,(float)t),bezierTangent((float)C[0].y,(float)C[1].y,(float)C[2].y,(float)C[3].y,(float)t),bezierTangent((float)C[0].z,(float)C[1].z,(float)C[2].z,(float)C[3].z,(float)t)); }
	
	
	public final myPoint Mouse() {return new myPoint(mouseX, mouseY,0);}                                          			// current mouse location
	public myVector MouseDrag() {return new myVector(mouseX-pmouseX,mouseY-pmouseY,0);};                     			// vector representing recent mouse displacement
	
	//public final int color(myPoint p){return color((int)p.x,(int)p.z,(int)p.y);}	//needs to be x,z,y for some reason - to match orientation of color frames in z-up 3d geometry
	public final int color(myPoint p){return color((int)p.x,(int)p.y,(int)p.z);}	
	
	// =====  vector functions
	public myVector V() {return new myVector(); };                                                                          // make vector (x,y,z)
	public myVector V(double x, double y, double z) {return new myVector(x,y,z); };                                            // make vector (x,y,z)
	public myVector V(myVector V) {return new myVector(V.x,V.y,V.z); };                                                          // make copy of vector V
	public myVector A(myVector A, myVector B) {return new myVector(A.x+B.x,A.y+B.y,A.z+B.z); };                                       // A+B
	public myVector A(myVector U, float s, myVector V) {return V(U.x+s*V.x,U.y+s*V.y,U.z+s*V.z);};                               // U+sV
	public myVector M(myVector U, myVector V) {return V(U.x-V.x,U.y-V.y,U.z-V.z);};                                              // U-V
	public myVector M(myVector V) {return V(-V.x,-V.y,-V.z);};                                                              // -V
	public myVector V(myVector A, myVector B) {return new myVector((A.x+B.x)/2.0,(A.y+B.y)/2.0,(A.z+B.z)/2.0); }                      // (A+B)/2
	public myVector V(myVector A, float s, myVector B) {return new myVector(A.x+s*(B.x-A.x),A.y+s*(B.y-A.y),A.z+s*(B.z-A.z)); };      // (1-s)A+sB
	public myVector V(myVector A, myVector B, myVector C) {return new myVector((A.x+B.x+C.x)/3.0,(A.y+B.y+C.y)/3.0,(A.z+B.z+C.z)/3.0); };  // (A+B+C)/3
	public myVector V(myVector A, myVector B, myVector C, myVector D) {return V(V(A,B),V(C,D)); };                                         // (A+B+C+D)/4
	public myVector V(double s, myVector A) {return new myVector(s*A.x,s*A.y,s*A.z); };                                           // sA
	public myVector V(double a, myVector A, double b, myVector B) {return A(V(a,A),V(b,B));}                                       // aA+bB 
	public myVector V(double a, myVector A, double b, myVector B, double c, myVector C) {return A(V(a,A,b,B),V(c,C));}                   // aA+bB+cC
	public myVector V(myPoint P, myPoint Q) {return new myVector(P,Q);};                                          // PQ
	public myVector N(myVector U, myVector V) {return V( U.y*V.z-U.z*V.y, U.z*V.x-U.x*V.z, U.x*V.y-U.y*V.x); };                  // UxV cross product (normal to both)
	public myVector N(myPoint A, myPoint B, myPoint C) {return N(V(A,B),V(A,C)); };                                                   // normal to triangle (A,B,C), not normalized (proportional to area)
	public myVector B(myVector U, myVector V) {return U(N(N(U,V),U)); }        
	
	
	public double d(myVector U, myVector V) {return U.x*V.x+U.y*V.y+U.z*V.z; };                                            //U*V dot product
	public double dot(myVector U, myVector V) {return U.x*V.x+U.y*V.y+U.z*V.z; };                                            //U*V dot product
	public double det2(myVector U, myVector V) {return -U.y*V.x+U.x*V.y; };                                       		// U|V det product
	public double det3(myVector U, myVector V) {double dist = d(U,V); return Math.sqrt(d(U,U)*d(V,V) - (dist*dist)); };                                // U|V det product
	public double m(myVector U, myVector V, myVector W) {return d(U,N(V,W)); };                                                 // (UxV)*W  mixed product, determinant - measures 6x the volume of the parallelapiped formed by myVectortors
	public double m(myPoint E, myPoint A, myPoint B, myPoint C) {return m(V(E,A),V(E,B),V(E,C));}                                    // det (EA EB EC) is >0 when E sees (A,B,C) clockwise
	public double n2(myVector V) {return (V.x*V.x)+(V.y*V.y)+(V.z*V.z);};                                                   // V*V    norm squared
	public double n(myVector V) {return  Math.sqrt(n2(V));};                                                                // ||V||  norm
	public double d(myPoint P, myPoint Q) {return  myPoint._dist(P, Q); };                            // ||AB|| distance
	public double area(myPoint A, myPoint B, myPoint C) {return n(N(A,B,C))/2; };                                               // area of triangle 
	public double volume(myPoint A, myPoint B, myPoint C, myPoint D) {return m(V(A,B),V(A,C),V(A,D))/6; };                           // volume of tet 
	public boolean parallel (myVector U, myVector V) {return n(N(U,V))<n(U)*n(V)*0.00001; }                              // true if U and V are almost parallel
	public double angle(myPoint A, myPoint B, myPoint C){return angle(V(A,B),V(A,C));}												//angle between AB and AC
	public double angle(myPoint A, myPoint B, myPoint C, myPoint D){return angle(U(A,B),U(C,D));}							//angle between AB and CD
	public double angle(myVector U, myVector V){double angle = Math.atan2(n(N(U,V)),d(U,V)),sign = m(U,V,V(0,0,1));if(sign<0){    angle=-angle;}	return angle;}
	public boolean cw(myVector U, myVector V, myVector W) {return m(U,V,W)>0; };                                               // (UxV)*W>0  U,V,W are clockwise
	public boolean cw(myPoint A, myPoint B, myPoint C, myPoint D) {return volume(A,B,C,D)>0; };                                     // tet is oriented so that A sees B, C, D clockwise 
	public boolean projectsBetween(myPoint P, myPoint A, myPoint B) {return dot(V(A,P),V(A,B))>0 && dot(V(B,P),V(B,A))>0 ; };
	public double distToLine(myPoint P, myPoint A, myPoint B) {double res = det3(U(A,B),V(A,P)); return Double.isNaN(res) ? 0 : res; };		//MAY RETURN NAN IF point P is on line
	public final myPoint projectionOnLine(myPoint P, myPoint A, myPoint B) {return P(A,dot(V(A,B),V(A,P))/dot(V(A,B),V(A,B)),V(A,B));}
	public boolean isSame(myPoint A, myPoint B) {return (A.x==B.x)&&(A.y==B.y)&&(A.z==B.z) ;}                                         // A==B
	public boolean isSame(myPoint A, myPoint B, double e) {return ((Math.abs(A.x-B.x)<e)&&(Math.abs(A.y-B.y)<e)&&(Math.abs(A.z-B.z)<e));}                   // ||A-B||<e
	
	public myVector W(double s,myVector V) {return V(s*V.x,s*V.y,s*V.z);}                                                      // sV
	
	public myVector U(myVector v){myVector u = new myVector(v); return u._normalize(); }
	public myVector U(myVector v, float d, myVector u){myVector r = new myVector(v,d,u); return r._normalize(); }
	public myVector Upt(myPoint v){myVector u = new myVector(v); return u._normalize(); }
	public myVector U(myPoint a, myPoint b){myVector u = new myVector(a,b); return u._normalize(); }
	public myVectorf Uf(myPoint a, myPoint b){myVectorf u = new myVectorf(a,b); return u._normalize(); }
	public myVector U(double x, double y, double z) {myVector u = new myVector(x,y,z); return u._normalize();}
	
	public myVector normToPlane(myPoint A, myPoint B, myPoint C) {return myVector._cross(new myVector(A,B),new myVector(A,C)); };   // normal to triangle (A,B,C), not normalized (proportional to area)
	
	public final void gl_normal(myVector V) {normal((float)V.x,(float)V.y,(float)V.z);}                                          // changes normal for smooth shading
	public final void gl_vertex(myPoint P) {vertex((float)P.x,(float)P.y,(float)P.z);}                                           // vertex for shading or drawing
	public final void gl_normal(myVectorf V) {normal(V.x,V.y,V.z);}                                          // changes normal for smooth shading
	public final void gl_vertex(myPointf P) {vertex(P.x,P.y,P.z);}                                           // vertex for shading or drawing
	/////////////
	// show functions 
	public final void showVec( myPoint ctr, double len, myVector v){line(ctr.x,ctr.y,ctr.z,ctr.x+(v.x)*len,ctr.y+(v.y)*len,ctr.z+(v.z)*len);}
	public final void show(myPoint P, double r,int fclr, int sclr, boolean flat) {//TODO make flat circles for points if flat
		pushMatrix(); pushStyle(); 
		if((fclr!= -1) && (sclr!= -1)){setColorValFill(fclr); setColorValStroke(sclr);}
		if(!flat){
			translate((float)P.x,(float)P.y,(float)P.z); 
			sphereDetail(5);
			sphere((float)r);
		} else {
			translate((float)P.x,(float)P.y,0); 
			this.circle(0,0,(float)r,(float)r);				
		}
		popStyle(); popMatrix();} // render sphere of radius r and center P)
	
	public final void show(myPoint P, double rad, int fclr, int sclr, int tclr, String txt) {
		pushMatrix(); pushStyle(); 
		if((fclr!= -1) && (sclr!= -1)){setColorValFill(fclr); setColorValStroke(sclr);}
		sphereDetail(5);
		translate((float)P.x,(float)P.y,(float)P.z); 
		setColorValFill(tclr);setColorValStroke(tclr);
		showOffsetText(1.2f * (float)rad,tclr, txt);
		popStyle(); popMatrix();} // render sphere of radius r and center P)
	
	public final void show(myPoint P, double r, int fclr, int sclr) {
		pushMatrix(); pushStyle(); 
		if((fclr!= -1) && (sclr!= -1)){setColorValFill(fclr); setColorValStroke(sclr);}
		sphereDetail(5);
		translate((float)P.x,(float)P.y,(float)P.z); 
		sphere((float)r); 
		popStyle(); popMatrix();} // render sphere of radius r and center P)
	
	public final void show(myPoint P, double r){show(P,r, gui_Black, gui_Black, false);}
	public final void show(myPoint P, String s) {text(s, (float)P.x, (float)P.y, (float)P.z); } // prints string s in 3D at P
	public final void show(myPoint P, String s, myVector D) {text(s, (float)(P.x+D.x), (float)(P.y+D.y), (float)(P.z+D.z));  } // prints string s in 3D at P+D
	public final void show(myPoint P, double r, String s, myVector D){show(P,r, gui_Black, gui_Black, false);pushStyle();setColorValFill(gui_Black);show(P,s,D);popStyle();}
	public final void show(myPoint P, double r, String s, myVector D, int clr, boolean flat){show(P,r, clr, clr, flat);pushStyle();setColorValFill(clr);show(P,s,D);popStyle();}
	public final void show(myPoint[] ara) {beginShape(); for(int i=0;i<ara.length;++i){gl_vertex(ara[i]);} endShape(CLOSE);};                     
	public final void show(myPoint[] ara, myVector norm) {beginShape();gl_normal(norm); for(int i=0;i<ara.length;++i){gl_vertex(ara[i]);} endShape(CLOSE);};   
	public final void showVec( myPointf ctr, float len, myVectorf v){line(ctr.x,ctr.y,ctr.z,ctr.x+(v.x)*len,ctr.y+(v.y)*len,ctr.z+(v.z)*len);}
	
	public final void showOffsetText(float d, int tclr, String txt){
		setColorValFill(tclr, 255);setColorValStroke(tclr, 255);
		text(txt, d, d,d); 
	}	
	public final void showOffsetText(myPointf loc, int tclr, String txt){
		setColorValFill(tclr, 255);setColorValStroke(tclr, 255);
		text(txt, loc.x, loc.y, loc.z); 
	}	
	public final void showOffsetText2D(float d, int tclr, String txt){
		setColorValFill(tclr, 255);setColorValStroke(tclr, 255);
		text(txt, d, d,0); 
	}
	public final void showOffsetTextAra(float d, int tclr, String[] txtAra){
		setColorValFill(tclr, 255);setColorValStroke(tclr, 255);
		float y = d;
		for (String txt : txtAra) {
			text(txt, d, y, d);
			y+=10;
		}
	}
		
	public final void show(myPointf P, float r,int fclr, int sclr, boolean flat) {//TODO make flat circles for points if flat
		pushMatrix(); pushStyle(); 
		if((fclr!= -1) && (sclr!= -1)){setColorValFill(fclr); setColorValStroke(sclr);}
		if(!flat){
			translate(P.x,P.y,P.z); 
			sphereDetail(5);
			sphere(r);
		} else {
			translate(P.x,P.y,0); 
			circle(0,0,r,r);				
		}
		popStyle(); popMatrix();
	} // render sphere of radius r and center P)	
	public final void showBox_ClrAra(myPointf P, float rad, int det, int[] fclr, int[] strkclr, int tclr, String txt) {
		pushMatrix(); pushStyle(); 
		translate(P.x,P.y,P.z);
		fill(255,255,255,150);
		stroke(0,0,0,255);
		rect(0,6.0f,txt.length()*7.8f,-15);
		tclr = gui_Black;		
		setFill(fclr,255); setStroke(strkclr,255);			
		sphereDetail(det);
		sphere(rad); 
		showOffsetText(1.2f * rad,tclr, txt);
		popStyle(); popMatrix();} // render sphere of radius r and center P)
	//translate to point, draw point and text
	public final void showNoBox_ClrAra(myPointf P, float rad, int det, int[] fclr, int[] strkclr, int tclr, String txt) {
		pushMatrix(); pushStyle(); 
		translate(P.x,P.y,P.z); 
		setFill(fclr,255); setStroke(strkclr,255);			
		sphereDetail(det);
		sphere(rad); 
		showOffsetText(1.2f * rad,tclr, txt);
		popStyle(); popMatrix();} // render sphere of radius r and center P)
	//textP is location of text relative to point
	public final void showNoBox_ClrAra(myPointf P, float rad, int det, int[] fclr, int[] strkclr, int tclr, myPointf txtP, String txt) {
		pushMatrix(); pushStyle(); 
		translate(P.x,P.y,P.z); 
		setFill(fclr,255); setStroke(strkclr,255);			
		sphereDetail(det);
		sphere(rad); 
		showOffsetText(txtP,tclr, txt);
		popStyle(); popMatrix();} // render sphere of radius r and center P)
	
	//textP is location of text relative to point
	public final void showCrclNoBox_ClrAra(myPointf P, float rad, int[] fclr, int[] strkclr, int tclr, myPointf txtP, String txt) {
		pushMatrix(); pushStyle(); 
		translate(P.x,P.y,P.z); 
		setFill(fclr,255); setStroke(strkclr,255);			
		ellipse(0,0,rad,rad); 
		ellipse(0,0,2,2);
		showOffsetText(txtP,tclr, txt);
		popStyle(); popMatrix();} // render sphere of radius r and center P)
	
	
	//show sphere of certain radius
	public final void show_ClrAra(myPointf P, float rad, int det, int[] fclr, int[] strkclr) {
		pushMatrix(); pushStyle(); 
		if((fclr!= null) && (strkclr!= null)){setFill(fclr,255); setStroke(strkclr,255);}
		sphereDetail(det);
		translate(P.x,P.y,P.z); 
		sphere(rad); 
		popStyle(); popMatrix();} // render sphere of radius r and center P)
	
	/////////////
	// show functions using color idxs 
	public final void showBox(myPointf P, float rad, int det, int[] clrs, String[] txtAra, float[] rectDims) {
		pushMatrix(); pushStyle(); 
			translate(P.x,P.y,P.z);
			setColorValFill(clrs[0],255); 
			setColorValStroke(clrs[1],255);
			sphereDetail(det);
			sphere(rad); 
			pushMatrix(); pushStyle();
			//make sure box doesn't extend off screen
				transToStayOnScreen(P,rectDims);
				fill(255,255,255,150);
				stroke(0,0,0,255);
				strokeWeight(2.5f);
				rect(rectDims);
				translate(rectDims[0],0,0);
				showOffsetTextAra(1.2f * rad, clrs[2], txtAra);
			popStyle(); popMatrix();
		popStyle(); popMatrix();} // render sphere of radius r and center P)
	
	public final void show(myPointf P, float rad, int det, int[] clrs) {//only call with set fclr and sclr - idx0 == fill, idx 1 == strk
		pushMatrix(); pushStyle(); 
		setColorValFill(clrs[0],255); 
		setColorValStroke(clrs[1],255);
		sphereDetail(det);
		translate(P.x,P.y,P.z); 
		sphere(rad); 
		popStyle(); popMatrix();} // render sphere of radius r and center P)
	
	public final void show(myPointf P, float rad, int det, int[] clrs, String[] txtAra) {//only call with set fclr and sclr - idx0 == fill, idx 1 == strk, idx2 == txtClr
		pushMatrix(); pushStyle(); 
		setColorValFill(clrs[0],255); 
		setColorValStroke(clrs[1],255);
		sphereDetail(det);
		translate(P.x,P.y,P.z); 
		sphere(rad); 
		showOffsetTextAra(1.2f * rad, clrs[2], txtAra);
		popStyle(); popMatrix();} // render sphere of radius r and center P)
	
	/////////////
	//base show function
	public final void show(myPointf P, float rad, int det){			
		pushMatrix(); pushStyle(); 
		fill(0,0,0,255); 
		stroke(0,0,0,255);
		sphereDetail(det);
		translate(P.x,P.y,P.z); 
		sphere(rad); 
		popStyle(); popMatrix();
	}
	
	public final void show(myPointf P, String s) {text(s, P.x, P.y, P.z); } // prints string s in 3D at P
	public final void show(myPointf P, String s, myVectorf D) {text(s, (P.x+D.x), (P.y+D.y),(P.z+D.z));  } // prints string s in 3D at P+D
	public final void show(myPointf P, float r, String s, myVectorf D){show(P,r, gui_Black, gui_Black, false);pushStyle();setColorValFill(gui_Black);show(P,s,D);popStyle();}
	public final void show(myPointf P, float r, String s, myVectorf D, int clr, boolean flat){show(P,r, clr, clr, flat);pushStyle();setColorValFill(clr);show(P,s,D);popStyle();}
	public final void show(myPointf[] ara) {beginShape(); for(int i=0;i<ara.length;++i){gl_vertex(ara[i]);} endShape(CLOSE);};                     
	public final void show(myPointf[] ara, myVectorf norm) {beginShape();gl_normal(norm); for(int i=0;i<ara.length;++i){gl_vertex(ara[i]);} endShape(CLOSE);};                     
	
	public final void showNoClose(myPoint[] ara) {beginShape(); for(int i=0;i<ara.length;++i){gl_vertex(ara[i]);} endShape();};                     
	public final void showNoClose(myPointf[] ara) {beginShape(); for(int i=0;i<ara.length;++i){gl_vertex(ara[i]);} endShape();};                     
	///end show functions
	
	public final void curveVertex(myPoint P) {curveVertex((float)P.x,(float)P.y);};                                           // curveVertex for shading or drawing
	public final void curve(myPoint[] ara) {if(ara.length == 0){return;}beginShape(); curveVertex(ara[0]);for(int i=0;i<ara.length;++i){curveVertex(ara[i]);} curveVertex(ara[ara.length-1]);endShape();};                      // volume of tet 	
	
	public boolean intersectPl(myPoint E, myVector T, myPoint A, myPoint B, myPoint C, myPoint X) { // if ray from E along T intersects triangle (A,B,C), return true and set proposal to the intersection point
		myVector EA=new myVector(E,A), AB=new myVector(A,B), AC=new myVector(A,C); 		double t = (float)(myVector._mixProd(EA,AC,AB) / myVector._mixProd(T,AC,AB));		X.set(myPoint._add(E,t,T));		return true;
	}	
	public final myPoint intersectPl(myPoint E, myVector T, myPoint A, myPoint B, myPoint C) { // if ray from E along T intersects triangle (A,B,C), return true and set proposal to the intersection point
		myVector EA=new myVector(E,A), AB=new myVector(A,B), AC=new myVector(A,C); 		
		double t = (float)(myVector._mixProd(EA,AC,AB) / myVector._mixProd(T,AC,AB));		
		return (myPoint._add(E,t,T));		
	}	
	// if ray from E along V intersects sphere at C with radius r, return t when intersection occurs
	public double intersectPt(myPoint E, myVector V, myPoint C, double r) { 
		myVector Vce = V(C,E);
		double CEdCE = Vce._dot(Vce), VdV = V._dot(V), VdVce = V._dot(Vce), b = 2 * VdVce, c = CEdCE - (r*r),
				radical = (b*b) - 4 *(VdV) * c;
		if(radical < 0) return -1;
		double t1 = (b + Math.sqrt(radical))/(2*VdV), t2 = (b - Math.sqrt(radical))/(2*VdV);			
		return ((t1 > 0) && (t2 > 0) ? Math.min(t1, t2) : ((t1 < 0 ) ? ((t2 < 0 ) ? -1 : t2) : t1) );
		
	}	
	
	public final void rect(float[] a){rect(a[0],a[1],a[2],a[3]);}				//rectangle from array of floats : x, y, w, h
	//this will translate the passed box dimensions to keep them on the screen
	//using p as start point and dims[2] and dims[3] as width and height
	public final void transToStayOnScreen(myPointf P, float[] rectDims) {
		float xLocSt = P.x + rectDims[0], xLocEnd = xLocSt + rectDims[2];
		float yLocSt = P.y + rectDims[1], yLocEnd = yLocSt + rectDims[3];
		float transX = 0.0f, transY = 0.0f;
		if (xLocSt < 0) {	transX = -1.0f * xLocSt;	} else if (xLocEnd > width) {transX = width - xLocEnd - 20;}
		if (yLocSt < 0) {	transY = -1.0f * yLocSt;	} else if (yLocEnd > height) {transY = height - yLocEnd - 20;}
		translate(transX,transY);		
	}
	
	
	
	/////////////////////		
	///color utils
	/////////////////////
	//		public final int  // set more colors using Menu >  Tools > Color Selector
	//		  black=0xff000000, 
	//		  white=0xffFFFFFF,
	//		  red=0xffFF0000, 
	//		  green=0xff00FF00, 
	//		  blue=0xff0000FF, 
	//		  yellow=0xffFFFF00, 
	//		  cyan=0xff00FFFF, 
	//		  magenta=0xffFF00FF,
	//		  grey=0xff818181, 
	//		  orange=0xffFFA600, 
	//		  brown=0xffB46005, 
	//		  metal=0xffB5CCDE, 
	//		  dgreen=0xff157901;
	//set color based on passed point r= x, g = z, b=y
	public final void fillAndShowLineByRBGPt(myPoint p, float x,  float y, float w, float h){
		fill((int)p.x,(int)p.y,(int)p.z);
		stroke((int)p.x,(int)p.y,(int)p.z);
		rect(x,y,w,h);
		//show(p,r,-1);
	}	
	public final myPoint WrldToScreen(myPoint wPt){return new myPoint(screenX((float)wPt.x,(float)wPt.y,(float)wPt.z),screenY((float)wPt.x,(float)wPt.y,(float)wPt.z),screenZ((float)wPt.x,(float)wPt.y,(float)wPt.z));}
	public final int[][] triColors = new int[][] {{gui_DarkMagenta,gui_DarkBlue,gui_DarkGreen,gui_DarkCyan}, {gui_LightMagenta,gui_LightBlue,gui_LightGreen,gui_TransCyan}};
	public final void setFill(int[] clr){setFill(clr,clr[3]);}
	public final void setStroke(int[] clr){setStroke(clr,clr[3]);}		
	public final void setFill(int[] clr, int alpha){fill(clr[0],clr[1],clr[2], alpha);}
	public final void setStroke(int[] clr, int alpha){stroke(clr[0],clr[1],clr[2], alpha);}
	public final void setColorValFill(int colorVal){ setColorValFill(colorVal,255);}
	public final void setColorValFill(int colorVal, int alpha){
		switch (colorVal){
			case gui_rnd				: { fill(random(255),random(255),random(255),alpha);break;}
			case gui_White  			: { fill(255,255,255,alpha);break; }
			case gui_Gray   			: { fill(120,120,120,alpha); break;}
			case gui_Yellow 			: { fill(255,255,0,alpha);break; }
			case gui_Cyan   			: { fill(0,255,255,alpha);  break; }
			case gui_Magenta			: { fill(255,0,255,alpha);break; }
			case gui_Red    			: { fill(255,0,0,alpha); break; }
			case gui_Blue				: { fill(0,0,255,alpha); break; }
			case gui_Green				: { fill(0,255,0,alpha);  break; } 
			case gui_DarkGray   		: { fill(80,80,80,alpha); break;}
			case gui_DarkRed    		: { fill(120,0,0,alpha);break;}
			case gui_DarkBlue   		: { fill(0,0,120,alpha); break;}
			case gui_DarkGreen  		: { fill(0,120,0,alpha); break;}
			case gui_DarkYellow 		: { fill(120,120,0,alpha); break;}
			case gui_DarkMagenta		: { fill(120,0,120,alpha); break;}
			case gui_DarkCyan   		: { fill(0,120,120,alpha); break;}	   
			case gui_LightGray   		: { fill(200,200,200,alpha); break;}
			case gui_LightRed    		: { fill(255,110,110,alpha); break;}
			case gui_LightBlue   		: { fill(110,110,255,alpha); break;}
			case gui_LightGreen  		: { fill(110,255,110,alpha); break;}
			case gui_LightYellow 		: { fill(255,255,110,alpha); break;}
			case gui_LightMagenta		: { fill(255,110,255,alpha); break;}
			case gui_LightCyan   		: { fill(110,255,255,alpha); break;}    	
			case gui_Black			 	: { fill(0,0,0,alpha);break;}//
			case gui_TransBlack  	 	: { fill(0x00010100);  break;}//	have to use hex so that alpha val is not lost    	
			case gui_FaintGray 		 	: { fill(77,77,77,alpha/3); break;}
			case gui_FaintRed 	 	 	: { fill(110,0,0,alpha/2);  break;}
			case gui_FaintBlue 	 	 	: { fill(0,0,110,alpha/2);  break;}
			case gui_FaintGreen 	 	: { fill(0,110,0,alpha/2);  break;}
			case gui_FaintYellow 	 	: { fill(110,110,0,alpha/2); break;}
			case gui_FaintCyan  	 	: { fill(0,110,110,alpha/2); break;}
			case gui_FaintMagenta  	 	: { fill(110,0,110,alpha/2); break;}
			case gui_TransGray 	 	 	: { fill(120,120,120,alpha/8); break;}//
			case gui_TransRed 	 	 	: { fill(255,0,0,alpha/2);  break;}
			case gui_TransBlue 	 	 	: { fill(0,0,255,alpha/2);  break;}
			case gui_TransGreen 	 	: { fill(0,255,0,alpha/2);  break;}
			case gui_TransYellow 	 	: { fill(255,255,0,alpha/2);break;}
			case gui_TransCyan  	 	: { fill(0,255,255,alpha/2);break;}
			case gui_TransMagenta  	 	: { fill(255,0,255,alpha/2);break;}
			case gui_OffWhite			: { fill(248,248,255,alpha);break; }
			default         			: { fill(255,255,255,alpha);break;}  	    	
		}//switch	
	}//setcolorValFill
	public final void setColorValStroke(int colorVal){ setColorValStroke(colorVal, 255);}
	public final void setColorValStroke(int colorVal, int alpha){
		switch (colorVal){
			case gui_White  	 	    : { stroke(255,255,255,alpha); break; }
			case gui_Gray   	 	    : { stroke(120,120,120,alpha); break;}
			case gui_Yellow      	    : { stroke(255,255,0,alpha); break; }
			case gui_Cyan   	 	    : { stroke(0,255,255,alpha); break; }
			case gui_Magenta	 	    : { stroke(255,0,255,alpha);  break; }
			case gui_Red    	 	    : { stroke(255,120,120,alpha); break; }
			case gui_Blue		 	    : { stroke(120,120,255,alpha); break; }
			case gui_Green		 	    : { stroke(120,255,120,alpha); break; }
			case gui_DarkGray    	    : { stroke(80,80,80,alpha); break; }
			case gui_DarkRed     	    : { stroke(120,0,0,alpha); break; }
			case gui_DarkBlue    	    : { stroke(0,0,120,alpha); break; }
			case gui_DarkGreen   	    : { stroke(0,120,0,alpha); break; }
			case gui_DarkYellow  	    : { stroke(120,120,0,alpha); break; }
			case gui_DarkMagenta 	    : { stroke(120,0,120,alpha); break; }
			case gui_DarkCyan    	    : { stroke(0,120,120,alpha); break; }	   
			case gui_LightGray   	    : { stroke(200,200,200,alpha); break;}
			case gui_LightRed    	    : { stroke(255,110,110,alpha); break;}
			case gui_LightBlue   	    : { stroke(110,110,255,alpha); break;}
			case gui_LightGreen  	    : { stroke(110,255,110,alpha); break;}
			case gui_LightYellow 	    : { stroke(255,255,110,alpha); break;}
			case gui_LightMagenta	    : { stroke(255,110,255,alpha); break;}
			case gui_LightCyan   		: { stroke(110,255,255,alpha); break;}		   
			case gui_Black				: { stroke(0,0,0,alpha); break;}
			case gui_TransBlack  		: { stroke(1,1,1,1); break;}	    	
			case gui_FaintGray 			: { stroke(120,120,120,250); break;}
			case gui_FaintRed 	 		: { stroke(110,0,0,alpha); break;}
			case gui_FaintBlue 	 		: { stroke(0,0,110,alpha); break;}
			case gui_FaintGreen 		: { stroke(0,110,0,alpha); break;}
			case gui_FaintYellow 		: { stroke(110,110,0,alpha); break;}
			case gui_FaintCyan  		: { stroke(0,110,110,alpha); break;}
			case gui_FaintMagenta  		: { stroke(110,0,110,alpha); break;}
			case gui_TransGray 	 		: { stroke(150,150,150,alpha/4); break;}
			case gui_TransRed 	 		: { stroke(255,0,0,alpha/2); break;}
			case gui_TransBlue 	 		: { stroke(0,0,255,alpha/2); break;}
			case gui_TransGreen 		: { stroke(0,255,0,alpha/2); break;}
			case gui_TransYellow 		: { stroke(255,255,0,alpha/2); break;}
			case gui_TransCyan  		: { stroke(0,255,255,alpha/2); break;}
			case gui_TransMagenta  		: { stroke(255,0,255,alpha/2); break;}
			case gui_OffWhite			: { stroke(248,248,255,alpha);break; }
			default         			: { stroke(55,55,255,alpha); break; }
		}//switch	
	}//setcolorValStroke	
	
	public final void setColorValFillAmb(int colorVal){ setColorValFillAmb(colorVal,255);}
	public final void setColorValFillAmb(int colorVal, int alpha){
		switch (colorVal){
			case gui_rnd				: { fill(random(255),random(255),random(255),alpha); ambient(120,120,120);break;}
			case gui_White  			: { fill(255,255,255,alpha); ambient(255,255,255); break; }
			case gui_Gray   			: { fill(120,120,120,alpha); ambient(120,120,120); break;}
			case gui_Yellow 			: { fill(255,255,0,alpha); ambient(255,255,0); break; }
			case gui_Cyan   			: { fill(0,255,255,alpha); ambient(0,255,alpha); break; }
			case gui_Magenta			: { fill(255,0,255,alpha); ambient(255,0,alpha); break; }
			case gui_Red    			: { fill(255,0,0,alpha); ambient(255,0,0); break; }
			case gui_Blue				: { fill(0,0,255,alpha); ambient(0,0,alpha); break; }
			case gui_Green				: { fill(0,255,0,alpha); ambient(0,255,0); break; } 
			case gui_DarkGray   		: { fill(80,80,80,alpha); ambient(80,80,80); break;}
			case gui_DarkRed    		: { fill(120,0,0,alpha); ambient(120,0,0); break;}
			case gui_DarkBlue   		: { fill(0,0,120,alpha); ambient(0,0,120); break;}
			case gui_DarkGreen  		: { fill(0,120,0,alpha); ambient(0,120,0); break;}
			case gui_DarkYellow 		: { fill(120,120,0,alpha); ambient(120,120,0); break;}
			case gui_DarkMagenta		: { fill(120,0,120,alpha); ambient(120,0,120); break;}
			case gui_DarkCyan   		: { fill(0,120,120,alpha); ambient(0,120,120); break;}		   
			case gui_LightGray   		: { fill(200,200,200,alpha); ambient(200,200,200); break;}
			case gui_LightRed    		: { fill(255,110,110,alpha); ambient(255,110,110); break;}
			case gui_LightBlue   		: { fill(110,110,255,alpha); ambient(110,110,alpha); break;}
			case gui_LightGreen  		: { fill(110,255,110,alpha); ambient(110,255,110); break;}
			case gui_LightYellow 		: { fill(255,255,110,alpha); ambient(255,255,110); break;}
			case gui_LightMagenta		: { fill(255,110,255,alpha); ambient(255,110,alpha); break;}
			case gui_LightCyan   		: { fill(110,255,255,alpha); ambient(110,255,alpha); break;}	    	
			case gui_Black			 	: { fill(0,0,0,alpha); ambient(0,0,0); break;}//
			case gui_TransBlack  	 	: { fill(0x00010100); ambient(0,0,0); break;}//	have to use hex so that alpha val is not lost    	
			case gui_FaintGray 		 	: { fill(77,77,77,alpha/3); ambient(77,77,77); break;}//
			case gui_FaintRed 	 	 	: { fill(110,0,0,alpha/2); ambient(110,0,0); break;}//
			case gui_FaintBlue 	 	 	: { fill(0,0,110,alpha/2); ambient(0,0,110); break;}//
			case gui_FaintGreen 	 	: { fill(0,110,0,alpha/2); ambient(0,110,0); break;}//
			case gui_FaintYellow 	 	: { fill(110,110,0,alpha/2); ambient(110,110,0); break;}//
			case gui_FaintCyan  	 	: { fill(0,110,110,alpha/2); ambient(0,110,110); break;}//
			case gui_FaintMagenta  	 	: { fill(110,0,110,alpha/2); ambient(110,0,110); break;}//
			case gui_TransGray 	 	 	: { fill(120,120,120,alpha/8); ambient(120,120,120); break;}//
			case gui_TransRed 	 	 	: { fill(255,0,0,alpha/2); ambient(255,0,0); break;}//
			case gui_TransBlue 	 	 	: { fill(0,0,255,alpha/2); ambient(0,0,alpha); break;}//
			case gui_TransGreen 	 	: { fill(0,255,0,alpha/2); ambient(0,255,0); break;}//
			case gui_TransYellow 	 	: { fill(255,255,0,alpha/2); ambient(255,255,0); break;}//
			case gui_TransCyan  	 	: { fill(0,255,255,alpha/2); ambient(0,255,alpha); break;}//
			case gui_TransMagenta  	 	: { fill(255,0,255,alpha/2); ambient(255,0,alpha); break;}//   	
			case gui_OffWhite			: { fill(248,248,255,alpha);ambient(248,248,255); break; }
			default         			: { fill(255,255,255,alpha); ambient(255,255,alpha); break; }	    
		
		}//switch	
	}//setcolorValFill
	
	//returns one of 30 predefined colors as an array (to support alpha)
	public final int[] getClr(int colorVal){		return getClr(colorVal, 255);	}//getClr
	public final int[] getClr(int colorVal, int alpha){
		switch (colorVal){
			case gui_Gray   		         : { return new int[] {120,120,120,alpha}; }
			case gui_White  		         : { return new int[] {255,255,255,alpha}; }
			case gui_Yellow 		         : { return new int[] {255,255,0,alpha}; }
			case gui_Cyan   		         : { return new int[] {0,255,255,alpha};} 
			case gui_Magenta		         : { return new int[] {255,0,255,alpha};}  
			case gui_Red    		         : { return new int[] {255,0,0,alpha};} 
			case gui_Blue			         : { return new int[] {0,0,255,alpha};}
			case gui_Green			         : { return new int[] {0,255,0,alpha};}  
			case gui_DarkGray   	         : { return new int[] {80,80,80,alpha};}
			case gui_DarkRed    	         : { return new int[] {120,0,0,alpha};}
			case gui_DarkBlue  	 	         : { return new int[] {0,0,120,alpha};}
			case gui_DarkGreen  	         : { return new int[] {0,120,0,alpha};}
			case gui_DarkYellow 	         : { return new int[] {120,120,0,alpha};}
			case gui_DarkMagenta	         : { return new int[] {120,0,120,alpha};}
			case gui_DarkCyan   	         : { return new int[] {0,120,120,alpha};}	   
			case gui_LightGray   	         : { return new int[] {200,200,200,alpha};}
			case gui_LightRed    	         : { return new int[] {255,110,110,alpha};}
			case gui_LightBlue   	         : { return new int[] {110,110,255,alpha};}
			case gui_LightGreen  	         : { return new int[] {110,255,110,alpha};}
			case gui_LightYellow 	         : { return new int[] {255,255,110,alpha};}
			case gui_LightMagenta	         : { return new int[] {255,110,255,alpha};}
			case gui_LightCyan   	         : { return new int[] {110,255,255,alpha};}
			case gui_Black			         : { return new int[] {0,0,0,alpha};}
			case gui_FaintGray 		         : { return new int[] {110,110,110,alpha};}
			case gui_FaintRed 	 	         : { return new int[] {110,0,0,alpha};}
			case gui_FaintBlue 	 	         : { return new int[] {0,0,110,alpha};}
			case gui_FaintGreen 	         : { return new int[] {0,110,0,alpha};}
			case gui_FaintYellow 	         : { return new int[] {110,110,0,alpha};}
			case gui_FaintCyan  	         : { return new int[] {0,110,110,alpha};}
			case gui_FaintMagenta  	         : { return new int[] {110,0,110,alpha};}    	
			case gui_TransBlack  	         : { return new int[] {1,1,1,alpha/2};}  	
			case gui_TransGray  	         : { return new int[] {110,110,110,alpha/2};}
			case gui_TransLtGray  	         : { return new int[] {180,180,180,alpha/2};}
			case gui_TransRed  	         	 : { return new int[] {110,0,0,alpha/2};}
			case gui_TransBlue  	         : { return new int[] {0,0,110,alpha/2};}
			case gui_TransGreen  	         : { return new int[] {0,110,0,alpha/2};}
			case gui_TransYellow  	         : { return new int[] {110,110,0,alpha/2};}
			case gui_TransCyan  	         : { return new int[] {0,110,110,alpha/2};}
			case gui_TransMagenta  	         : { return new int[] {110,0,110,alpha/2};}	
			case gui_TransWhite  	         : { return new int[] {220,220,220,alpha/2};}	
			case gui_OffWhite				 : { return new int[] {255,255,235,alpha};}
			default         		         : { return new int[] {255,255,255,alpha};}    
		}//switch
	}//getClr
	
	public final int getRndClrInt(){return (int)random(0,23);}		//return a random color flag value from below
	public final int[] getRndClr(int alpha){return new int[]{(int)random(0,255),(int)random(0,255),(int)random(0,255),alpha};	}
	public final int[] getRndClr2(){return new int[]{(int)random(50,255),(int)random(25,200),(int)random(80,255),255};	}
	public final int[] getRndClr2(int alpha){return new int[]{(int)random(50,255),(int)random(25,200),(int)random(80,255),alpha};	}
	public final int[] getRndClr(){return getRndClr(255);	}		
	public final Integer[] getClrMorph(int a, int b, double t){return getClrMorph(getClr(a), getClr(b), t);}    
	public final Integer[] getClrMorph(int[] a, int[] b, double t){
		if(t==0){return new Integer[]{a[0],a[1],a[2],a[3]};} else if(t==1){return new Integer[]{b[0],b[1],b[2],b[3]};}
		return new Integer[]{(int)(((1.0f-t)*a[0])+t*b[0]),(int)(((1.0f-t)*a[1])+t*b[1]),(int)(((1.0f-t)*a[2])+t*b[2]),(int)(((1.0f-t)*a[3])+t*b[3])};
	}

	//used to generate random color
	public static final int gui_rnd = -1;
	//color indexes
	public static final int gui_Black 	= 0;
	public static final int gui_White 	= 1;	
	public static final int gui_Gray 	= 2;
	
	public static final int gui_Red 	= 3;
	public static final int gui_Blue 	= 4;
	public static final int gui_Green 	= 5;
	public static final int gui_Yellow 	= 6;
	public static final int gui_Cyan 	= 7;
	public static final int gui_Magenta = 8;
	
	public static final int gui_LightRed = 9;
	public static final int gui_LightBlue = 10;
	public static final int gui_LightGreen = 11;
	public static final int gui_LightYellow = 12;
	public static final int gui_LightCyan = 13;
	public static final int gui_LightMagenta = 14;
	public static final int gui_LightGray = 15;
	
	public static final int gui_DarkCyan = 16;
	public static final int gui_DarkYellow = 17;
	public static final int gui_DarkGreen = 18;
	public static final int gui_DarkBlue = 19;
	public static final int gui_DarkRed = 20;
	public static final int gui_DarkGray = 21;
	public static final int gui_DarkMagenta = 22;
	
	public static final int gui_FaintGray = 23;
	public static final int gui_FaintRed = 24;
	public static final int gui_FaintBlue = 25;
	public static final int gui_FaintGreen = 26;
	public static final int gui_FaintYellow = 27;
	public static final int gui_FaintCyan = 28;
	public static final int gui_FaintMagenta = 29;
	
	public static final int gui_TransBlack = 30;
	public static final int gui_TransGray = 31;
	public static final int gui_TransMagenta = 32;	
	public static final int gui_TransLtGray = 33;
	public static final int gui_TransRed = 34;
	public static final int gui_TransBlue = 35;
	public static final int gui_TransGreen = 36;
	public static final int gui_TransYellow = 37;
	public static final int gui_TransCyan = 38;	
	public static final int gui_TransWhite = 39;	
	public static final int gui_OffWhite = 40;
	

}//my_procApplet
