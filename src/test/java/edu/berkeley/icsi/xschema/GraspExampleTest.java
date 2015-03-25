package edu.berkeley.icsi.xschema;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.junit.Before;
import org.junit.Test;

import uk.ac.imperial.pipe.dsl.ANormalArc;
import uk.ac.imperial.pipe.dsl.APetriNet;
import uk.ac.imperial.pipe.dsl.APlace;
import uk.ac.imperial.pipe.dsl.AToken;
import uk.ac.imperial.pipe.dsl.AnExternalTransition;
import uk.ac.imperial.pipe.dsl.AnImmediateTransition;
import uk.ac.imperial.pipe.exceptions.IncludeException;
import uk.ac.imperial.pipe.exceptions.PetriNetComponentException;
import uk.ac.imperial.pipe.exceptions.PetriNetComponentNotFoundException;
import uk.ac.imperial.pipe.models.petrinet.Arc;
import uk.ac.imperial.pipe.models.petrinet.DiscreteExternalTransition;
import uk.ac.imperial.pipe.models.petrinet.DiscretePlace;
import uk.ac.imperial.pipe.models.petrinet.DiscreteTransition;
import uk.ac.imperial.pipe.models.petrinet.InboundArc;
import uk.ac.imperial.pipe.models.petrinet.InboundNormalArc;
import uk.ac.imperial.pipe.models.petrinet.IncludeHierarchy;
import uk.ac.imperial.pipe.models.petrinet.OutboundArc;
import uk.ac.imperial.pipe.models.petrinet.OutboundNormalArc;
import uk.ac.imperial.pipe.models.petrinet.PetriNet;
import uk.ac.imperial.pipe.models.petrinet.Place;
import uk.ac.imperial.pipe.models.petrinet.PlaceStatusInterface;
import uk.ac.imperial.pipe.models.petrinet.Transition;
import uk.ac.imperial.pipe.runner.FiringWriter;
import uk.ac.imperial.pipe.runner.InterfaceException;
import uk.ac.imperial.pipe.runner.JsonParameters;
import uk.ac.imperial.pipe.runner.PetriNetRunner;
import uk.ac.imperial.pipe.runner.Runner;
/**
 * The Grasp example shows how to build an x-schema that executes other x-schemas.
 * {@link https://github.com/sjdayday/xschema/wiki}  
 * The test methods below show the steps.  
 * Note that because each test is independent, they may not execute in the order in which they appear in the file.
 * 
 * The tests assert markings at points of interest.  To see the actual results file for a given test, 
 * just run that test by itself, and then look at the report.csv file in your xschema project root directory
 * (if running eclipse, right click on the test in the Junit output, and select "Run")
 * @author stevedoubleday
 *
 */
public class GraspExampleTest implements PropertyChangeListener {

    private static  String CLOSE_SENSED = null;
	private Runner runner;
	private File file;
	private boolean tokenEvent;
	String filename = "report.csv";
	private List<String> linelist;
	private IncludeHierarchy includes;
	private Map<String,String> tokenweights;
	private boolean prepareTransitionExpanded;
	private Transition targetTransition;
	private Place beforePlace;
	private Place afterPlace;
	private Transition t10;
	private Transition t11;
	private boolean closeSensed;

	@Before
	public void setUp() {
		cleanupFile(filename); 
		buildTokenWeights(); 
		prepareTransitionExpanded = false; 
		closeSensed = true;
		tokenEvent = false; 
	}

	@Test
	public void basicXschemaBuilt() throws Exception {
		PetriNet basicControl = buildBasicNet("net1"); 
		runner = new PetriNetRunner(basicControl); 
		runner.markPlace("Enabled", "Default", 1);
		run();
//		printResults();
		checkLine("", 0, "\"Round\",\"Transition\",\"Done\",\"Enabled\",\"Ongoing\",\"Ready\"");
		checkLine("after setup, Enabled is marked by client request", 
				1, "0,\"\",0,0,0,0");
		checkLine("...but not visible because Prepare transition fires before report record is generated",
				2, "1,\"Prepare\",0,0,0,1");
		checkLine("", 4, "3,\"Finish\",1,0,0,0");
	}

	@Test
	public void closeHandXschemaBuilt() throws Exception {
		PetriNet closeHand = buildCloseHand(); 
		runner = new PetriNetRunner(closeHand); 
		runner.markPlace("Enabled", "Default", 1);
		CLOSE_SENSED = "Close_sensed"; 
		runner.setTransitionContext("Close", this);
		run(); 
		checkLine("", 0, "\"Round\",\"Transition\",\"Close_sensed\",\"Closing\",\"Done\",\"Enabled\",\"Ongoing\",\"P4\",\"Ready\"");
		checkLine("external transition fired...", 4, "3,\"Close\",0,1,0,0,1,0,0");
		checkLine("...marking close_sensed place but not visible here", 5, "4,\"Finish\",0,0,1,0,0,0,0");
//		printResults();
	}
	@Test
	public void graspXschemaIncludesCloseHand() throws Exception {
		PetriNet basicControl = buildBasicNet("net1"); 
		PetriNet closeHand = buildCloseHand(); 
		includes = new IncludeHierarchy(basicControl, "Grasp"); 
		includes.include(closeHand, "Close_hand");
//		build a "merge arc" by hand the first time...
		includes.getInclude("Close_hand").addToInterface(closeHand.getComponent("Enabled", Place.class), true, false, false, false);    
		includes.addAvailablePlaceToPetriNet(includes.getInterfacePlace("Close_hand.Enabled"));
		Place graspCloseHandEnabled = includes.getInterfacePlace("Close_hand.Enabled"); 
		Transition start = basicControl.getComponent("Start",Transition.class);
		OutboundArc arcOut = new OutboundNormalArc(start, graspCloseHandEnabled, tokenweights);
		basicControl.add(arcOut); 
//		...equivalent to this:
//		buildMergeArc(false, includes, "Close_hand", "Enabled", "Start", "Close_hand.Enabled"); 
    	
		buildMergeArc(true, includes, "Close_hand", "Done", "Finish", "Close_hand.Done"); 
    	
		runner = new PetriNetRunner(includes.getPetriNet()); 
		runner.markPlace("Grasp.Enabled", "Default", 1);
		CLOSE_SENSED = "Grasp.Close_hand.Close_sensed"; 
		runner.setTransitionContext("Grasp.Close_hand.Close", this);
		run(); 
//		printResults();
		checkLine("", 0, "\"Round\",\"Transition\",\"Grasp.Close_hand.Close_sensed\",\"Grasp.Close_hand.Closing\",\"Grasp.Close_hand.Done\","
				+ "\"Grasp.Close_hand.Enabled\",\"Grasp.Close_hand.Ongoing\",\"Grasp.Close_hand.P4\",\"Grasp.Close_hand.Ready\",\"Grasp.Done\","
				+ "\"Grasp.Enabled\",\"Grasp.Ongoing\",\"Grasp.Ready\"");
		checkLine("", 2, "1,\"Grasp.Prepare\",0,0,0,0,0,0,0,0,0,0,1");
		checkLine("", 3, "2,\"Grasp.Start\",0,0,0,1,0,0,0,0,0,1,0");
		checkLine("", 4, "3,\"Grasp.Close_hand.Prepare\",0,0,0,0,0,0,1,0,0,1,0");
		checkLine("", 5, "4,\"Grasp.Close_hand.Start\",0,0,0,0,1,1,0,0,0,1,0");
		checkLine("", 6, "5,\"Grasp.Close_hand.Close\",0,1,0,0,1,0,0,0,0,1,0");
		checkLine("", 7, "6,\"Grasp.Close_hand.Finish\",0,0,1,0,0,0,0,0,0,1,0");
		checkLine("", 8, "7,\"Grasp.Finish\",0,0,0,0,0,0,0,1,0,0,0");
		
	}
	@Test
	public void expandSingleTransitionDefaultsToIncludingBasicControlXschema() throws Exception {
		PetriNet basicControl = buildBasicNet("net1"); 
		includes = new IncludeHierarchy(basicControl, "Grasp");
		findComponentsAffectedByExpansion(includes, "Prepare");
		expandTransition(includes, "Prepare", "Pre-shape");
		expandTransition(includes, "Prepare", "Approach");
		removeTransition(includes, "Prepare"); 
		runner = new PetriNetRunner(includes.getPetriNet()); 
		runner.markPlace("Grasp.Enabled", "Default", 1);
		run(); 
//		printResults();
		checkLine("", 0, "\"Round\",\"Transition\",\"Grasp.Approach.Done\",\"Grasp.Approach.Enabled\","
				+ "\"Grasp.Approach.Ongoing\",\"Grasp.Approach.Ready\",\"Grasp.Done\",\"Grasp.Enabled\","
				+ "\"Grasp.Ongoing\",\"Grasp.Pre-shape.Done\",\"Grasp.Pre-shape.Enabled\","
				+ "\"Grasp.Pre-shape.Ongoing\",\"Grasp.Pre-shape.Ready\",\"Grasp.Ready\"");
		checkLine("", 2, "1,\"Grasp.Pre-Prepare\",0,1,0,0,0,0,0,0,1,0,0,0");
		checkLine("", 3, "2,\"Grasp.Approach.Prepare\",0,0,0,1,0,0,0,0,1,0,0,0");
		checkLine("random alternation between child nets",
				4, "3,\"Grasp.Pre-shape.Prepare\",0,0,0,1,0,0,0,0,0,0,1,0");
		checkLine("", 5, "4,\"Grasp.Approach.Start\",0,0,1,0,0,0,0,0,0,0,1,0");
		checkLine("Approach finishes...", 6, "5,\"Grasp.Approach.Finish\",1,0,0,0,0,0,0,0,0,0,1,0");
		checkLine("...Pre-shape continues", 7, "6,\"Grasp.Pre-shape.Start\",1,0,0,0,0,0,0,0,0,1,0,0");
		checkLine("", 8, "7,\"Grasp.Pre-shape.Finish\",1,0,0,0,0,0,0,1,0,0,0,0");
		checkLine("once both children finish, grasp continues",
				9, "8,\"Grasp.Post-Prepare\",0,0,0,0,0,0,0,0,0,0,0,1");
		checkLine("", 10, "9,\"Grasp.Start\",0,0,0,0,0,0,1,0,0,0,0,0");
		checkLine("", 11, "10,\"Grasp.Finish\",0,0,0,0,1,0,0,0,0,0,0,0");
	}
	@Test
	public void closeHandXschemaBuiltWithSuspendSemantics() throws Exception {
		PetriNet basicControl = buildBasicNet("net1"); 
		PetriNet closeHand = buildCloseHand(); 
		includes = new IncludeHierarchy(basicControl, "Grasp"); 
		includes.include(closeHand, "Close_hand");
		buildMergeArc(false, includes, "Close_hand", "Enabled", "Start", "Close_hand.Enabled"); 
		buildMergeArc(true, includes, "Close_hand", "Done", "Finish", "Close_hand.Done"); 

		addSuspend(closeHand); 
		Place missed = addMissedExternalInputPlaceToCloseHandXschema(closeHand); 
		Transition transition = closeHand.getComponent("Suspend", Transition.class);
		InboundArc inbound = new InboundNormalArc(missed, transition, tokenweights);
		closeHand.add(inbound);
		closeSensed = false; 

		addSuspend(basicControl); 
		addExternalOutputStatusToGraspSuspendedPlace(basicControl);

		buildMergeArc(true, includes, "Close_hand", "Suspended", "Suspend", "Close_hand.Suspended"); 
    	
		runner = new PetriNetRunner(includes.getPetriNet()); 
		runner.markPlace("Grasp.Enabled", "Default", 1);
		CLOSE_SENSED = "Grasp.Close_hand.Close_sensed"; 
		runner.setTransitionContext("Grasp.Close_hand.Close", this);
		runner.listenForTokenChanges(this, "Grasp.Suspended");
		run(); 
		assertTrue(tokenEvent); 
//		printResults();
		checkLine("", 0, "\"Round\",\"Transition\",\"Grasp.Close_hand.Close_sensed\",\"Grasp.Close_hand.Closing\","
				+ "\"Grasp.Close_hand.Done\",\"Grasp.Close_hand.Enabled\",\"Grasp.Close_hand.Missed\","
				+ "\"Grasp.Close_hand.Ongoing\",\"Grasp.Close_hand.P4\",\"Grasp.Close_hand.Ready\","
				+ "\"Grasp.Close_hand.Suspended\",\"Grasp.Done\",\"Grasp.Enabled\",\"Grasp.Ongoing\","
				+ "\"Grasp.Ready\",\"Grasp.Suspended\"");
		checkLine("", 2, "1,\"Grasp.Prepare\",0,0,0,0,0,0,0,0,0,0,0,0,1,0");
		checkLine("", 3, "2,\"Grasp.Start\",0,0,0,1,0,0,0,0,0,0,0,1,0,0");
		checkLine("", 4, "3,\"Grasp.Close_hand.Prepare\",0,0,0,0,0,0,0,1,0,0,0,1,0,0");
		checkLine("", 5, "4,\"Grasp.Close_hand.Start\",0,0,0,0,0,1,1,0,0,0,0,1,0,0");
		checkLine("", 6, "5,\"Grasp.Close_hand.Close\",0,1,0,0,0,1,0,0,0,0,0,1,0,0");
		checkLine("still Closing -- a realistic xschema would clear this",
				7, "6,\"Grasp.Close_hand.Suspend\",0,1,0,0,0,0,0,0,1,0,0,1,0,0");
		checkLine("", 8, "7,\"Grasp.Suspend\",0,1,0,0,0,0,0,0,0,0,0,0,0,1");
	}
	@Test
	public void completeGraspXschema() throws Exception {
		PetriNet basicControl = buildBasicNet("net1"); 
		
		includes = new IncludeHierarchy(basicControl, "Grasp");
		findComponentsAffectedByExpansion(includes, "Prepare");
		expandTransition(includes, "Prepare", "Pre-shape");
		expandTransition(includes, "Prepare", "Approach");
		removeTransition(includes, "Prepare"); 
		addExternalTransitionToApproach(includes); 
		JsonParameters parameters = new JsonParameters("{\"transitions\":{\"Grasp.Approach.T3\":{\"num\":1}}}"); 
		parameters.setActiveTransition("Grasp.Approach.T3");

		PetriNet closeHand = buildCloseHand(); 
		includes.include(closeHand, "Close_hand");
		buildMergeArc(false, includes, "Close_hand", "Enabled", "Start", "Close_hand.Enabled"); 
		buildMergeArc(true, includes, "Close_hand", "Done", "Finish", "Close_hand.Done"); 
		
		addSuspend(closeHand); 
		Place missed = addMissedExternalInputPlaceToCloseHandXschema(closeHand); 
		Transition transition = closeHand.getComponent("Suspend", Transition.class);
		InboundArc inbound = new InboundNormalArc(missed, transition, tokenweights);
		closeHand.add(inbound);
		closeSensed = false; 
		
		addSuspend(basicControl); 
		addExternalOutputStatusToGraspSuspendedPlace(basicControl);
		
		buildMergeArc(true, includes, "Close_hand", "Suspended", "Suspend", "Close_hand.Suspended"); 
		
		runner = new PetriNetRunner(includes.getPetriNet()); 
		runner.markPlace("Grasp.Enabled", "Default", 1);
		CLOSE_SENSED = "Grasp.Close_hand.Close_sensed"; 
		runner.setTransitionContext("Grasp.Approach.T3", parameters);
		runner.setTransitionContext("Grasp.Close_hand.Close", this);
		runner.listenForTokenChanges(this, "Grasp.Suspended");
		run(); 
		assertTrue(tokenEvent); 
		assertEquals(2, parameters.getActiveTransition().getJsonObject().getInt("num")); 
//		printResults();
		checkLine("", 0, "\"Round\",\"Transition\",\"Grasp.Approach.Done\",\"Grasp.Approach.Enabled\","
				+ "\"Grasp.Approach.Ongoing\",\"Grasp.Approach.P4\",\"Grasp.Approach.Ready\","
				+ "\"Grasp.Close_hand.Close_sensed\",\"Grasp.Close_hand.Closing\",\"Grasp.Close_hand.Done\","
				+ "\"Grasp.Close_hand.Enabled\",\"Grasp.Close_hand.Missed\",\"Grasp.Close_hand.Ongoing\","
				+ "\"Grasp.Close_hand.P4\",\"Grasp.Close_hand.Ready\",\"Grasp.Close_hand.Suspended\","
				+ "\"Grasp.Done\",\"Grasp.Enabled\",\"Grasp.Ongoing\",\"Grasp.Pre-shape.Done\","
				+ "\"Grasp.Pre-shape.Enabled\",\"Grasp.Pre-shape.Ongoing\",\"Grasp.Pre-shape.Ready\","
				+ "\"Grasp.Ready\",\"Grasp.Suspended\"");
		checkLine("", 2, "1,\"Grasp.Pre-Prepare\",0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0");
		checkLine("", 3, "2,\"Grasp.Approach.Prepare\",0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0");
		checkLine("", 4, "3,\"Grasp.Pre-shape.Prepare\",0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0");
		checkLine("", 5, "4,\"Grasp.Approach.Start\",0,0,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0");
		checkLine("", 6, "5,\"Grasp.Pre-shape.Start\",0,0,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0");
		checkLine("", 7, "6,\"Grasp.Pre-shape.Finish\",0,0,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0");
		checkLine("", 8, "7,\"Grasp.Approach.T3\",0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0");
		checkLine("", 9, "8,\"Grasp.Approach.Finish\",1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0");
		checkLine("", 10, "9,\"Grasp.Post-Prepare\",0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0");
		checkLine("", 11, "10,\"Grasp.Start\",0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,1,0,0,0,0,0,0");
		checkLine("", 12, "11,\"Grasp.Close_hand.Prepare\",0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,0,0,0,0,0");
		checkLine("", 13, "12,\"Grasp.Close_hand.Start\",0,0,0,0,0,0,0,0,0,0,1,1,0,0,0,0,1,0,0,0,0,0,0");
		checkLine("", 14, "13,\"Grasp.Close_hand.Close\",0,0,0,0,0,0,1,0,0,0,1,0,0,0,0,0,1,0,0,0,0,0,0");
		checkLine("", 15, "14,\"Grasp.Close_hand.Suspend\",0,0,0,0,0,0,1,0,0,0,0,0,0,1,0,0,1,0,0,0,0,0,0");
		checkLine("", 16, "15,\"Grasp.Suspend\",0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1");
	}

	private void addExternalTransitionToApproach(IncludeHierarchy includes) throws Exception {
		PetriNet approach = includes.getInclude("Approach").getPetriNet(); 
		Place p4 = new DiscretePlace("P4");
		approach.add(p4);
		Transition t3 = new DiscreteExternalTransition("T3", "T3","edu.berkeley.icsi.xschema.TestingApproachExternalTransition"); 
		approach.add(t3);
		approach.add(new OutboundNormalArc(approach.getComponent("Start", Transition.class), p4, tokenweights));    
		approach.add(new InboundNormalArc(p4, t3, tokenweights));    
		
	}

	private void addExternalOutputStatusToGraspSuspendedPlace(PetriNet basicControl) throws Exception {
		Place suspended = basicControl.getComponent("Suspended", Place.class); 
		PlaceStatusInterface status = new PlaceStatusInterface(suspended, includes);  
		status.setExternal(true);
		status.setOutputOnlyArcConstraint(true);
		status.update(); 
		suspended.setStatus(status);
	}

	private Place addMissedExternalInputPlaceToCloseHandXschema(PetriNet closeHand) throws Exception {
		Place missed = new DiscretePlace("P000");
		PlaceStatusInterface status = new PlaceStatusInterface(missed, includes.getInclude("Close_hand"));  
		status.setExternal(true);
		status.setInputOnlyArcConstraint(true);
		status.update(); 
		missed.setStatus(status);
		closeHand.add(missed);
		name(closeHand, Place.class, "P000", "Missed");
		return missed; 
	}

	private void addSuspend(PetriNet net) throws  Exception {
		Place suspended = new DiscretePlace("P00");
		Transition suspend = new DiscreteTransition("T00"); 
		Place ongoing = net.getComponent("Ongoing", Place.class);
		net.add(suspended);
		net.add(suspend);
		name(net, Place.class, "P00", "Suspended"); 
		name(net, Transition.class, "T00", "Suspend"); 
		InboundArc arcIn = new InboundNormalArc(ongoing, suspend, tokenweights); 
		OutboundArc arcOut = new OutboundNormalArc(suspend, suspended, tokenweights);
		net.add(arcIn);
		net.add(arcOut);
	}

	private void removeTransition(IncludeHierarchy includes, String transition) {
		// perhaps we shouldn't have to explicitly delete the Arcs; may be bug in PIPECore
		InboundArc beforeArc = includes.getPetriNet().inboundArcs(targetTransition).iterator().next();
		OutboundArc afterArc = includes.getPetriNet().outboundArcs(targetTransition).iterator().next();
		includes.getPetriNet().removeArc(beforeArc);
		includes.getPetriNet().removeArc(afterArc);

		includes.getPetriNet().removeTransition(targetTransition);
//		for (Arc arc : includes.getPetriNet().getArcs()) {
//			System.out.println(arc.getId());
//		}
	}

	private void expandTransition(IncludeHierarchy includes, String transition, String child) throws Exception {
		PetriNet expanded = buildBasicNet(child+"net"); 
		includes.include(expanded, child);
		buildPrePostTransitions(includes, transition);
		includes.getPetriNet().add(new InboundNormalArc(beforePlace, t10, tokenweights));    
		buildMergeArc(false, includes, child, "Enabled", "Pre-"+transition, child+".Enabled"); 
		includes.getPetriNet().add(new OutboundNormalArc(t11, afterPlace, tokenweights));    
		buildMergeArc(true, includes, child, "Done", "Post-"+transition, child+".Done"); 
	}

	private void buildPrePostTransitions(IncludeHierarchy includes,
		String transition) throws PetriNetComponentException {
		if (!prepareTransitionExpanded) {
			t10 = new DiscreteTransition("T10");  
			t11 = new DiscreteTransition("T11"); 
			includes.getPetriNet().add(t10);
			includes.getPetriNet().add(t11);
			name(includes.getPetriNet(), Transition.class, "T10", "Pre-"+transition); 
			name(includes.getPetriNet(), Transition.class, "T11", "Post-"+transition); 
			prepareTransitionExpanded = true; 
		}
	}

	private void findComponentsAffectedByExpansion(IncludeHierarchy includes,
		String transition) throws PetriNetComponentNotFoundException {
		targetTransition = includes.getPetriNet().getComponent(transition, Transition.class); 
		beforePlace = includes.getPetriNet().inboundArcs(targetTransition).iterator().next().getSource();
		afterPlace = includes.getPetriNet().outboundArcs(targetTransition).iterator().next().getTarget();
	}

	@SuppressWarnings("rawtypes")
	private void buildMergeArc(boolean inbound,
		IncludeHierarchy parent, String child, String homePlace, String transition, String awayPlace) 
				throws IncludeException, PetriNetComponentNotFoundException, PetriNetComponentException {
		parent.getInclude(child).addToInterface(parent.getInclude(child).getPetriNet().
				getComponent(homePlace, Place.class), true, false, false, false);    
		parent.addAvailablePlaceToPetriNet(parent.getInterfacePlace(awayPlace));
		Arc arc = (inbound) ? new InboundNormalArc(parent.getInterfacePlace(awayPlace), 
				parent.getPetriNet().getComponent(transition,Transition.class), tokenweights)   
			    : new OutboundNormalArc(parent.getPetriNet().getComponent(transition,Transition.class), 
						parent.getInterfacePlace(awayPlace), tokenweights)	;
		parent.getPetriNet().add(arc); 
	}
	private void buildTokenWeights() {
		tokenweights = new HashMap<String, String>(); 
		tokenweights.put("Default", "1");
	}
	private void run() throws Exception {
		runner.addPropertyChangeListener(new FiringWriter("report.csv"));
		runner.setFiringLimit(100);
		runner.setSeed(123456l); 
		runner.run();
		buildLineList(); 
	}

	@SuppressWarnings("unused")
	private void printResults() {
		for (String line : linelist) {
			System.out.println(line);
		}
	}
	private void checkLine(String comment, int i, String expected) throws IOException {
		assertEquals(comment, expected, linelist.get(i));
	}

	private void buildLineList() throws FileNotFoundException, IOException {
		BufferedReader fileReader = new BufferedReader(new FileReader(filename)); 
		linelist = new ArrayList<String>(); 
		String line = fileReader.readLine(); 
		while (line != null) {
			linelist.add(line); 
			line = fileReader.readLine(); 
		}
		fileReader.close();
	}

    private PetriNet buildBasicNet(String name) {
    	PetriNet net = APetriNet.named(name).and(AToken.called("Default").withColor(Color.BLACK)).
    		and(APlace.withId("P0").externallyAccessible()).and(APlace.withId("P1")).and(APlace.withId("P2")).and(APlace.withId("P3")).
    		and(AnImmediateTransition.withId("T0")).and(AnImmediateTransition.withId("T1")).and(AnImmediateTransition.withId("T2")).		
    		and(ANormalArc.withSource("P0").andTarget("T0").with("1", "Default").token()).
    		and(ANormalArc.withSource("T0").andTarget("P1").with("1", "Default").token()).
    		and(ANormalArc.withSource("P1").andTarget("T1").with("1", "Default").token()).
    		and(ANormalArc.withSource("T1").andTarget("P2").with("1", "Default").token()).
    		and(ANormalArc.withSource("P2").andTarget("T2").with("1", "Default").token()).
    		andFinally(ANormalArc.withSource("T2").andTarget("P3").with("1", "Default").token());
    	name(net, Place.class, "P0", "Enabled"); 
    	name(net, Place.class, "P1", "Ready"); 
    	name(net, Place.class, "P2", "Ongoing"); 
    	name(net, Place.class, "P3", "Done"); 
    	name(net, Transition.class, "T0", "Prepare"); 
    	name(net, Transition.class, "T1", "Start"); 
    	name(net, Transition.class, "T2", "Finish"); 
    	return net; 
    }
    
    private PetriNet buildCloseHand() {
    	// P0 / Enabled is externally accessible for testing, not because required for Grasp xschema
    	PetriNet net = APetriNet.named("Close_hand").and(AToken.called("Default").withColor(Color.BLACK)).
    		and(APlace.withId("P0").externallyAccessible()).and(APlace.withId("P1")).and(APlace.withId("P2")).and(APlace.withId("P3")).
    		and(APlace.withId("P4")).and(APlace.withId("P5")).and(APlace.withId("P6").externallyAccessible()).
    		and(AnImmediateTransition.withId("T0")).and(AnImmediateTransition.withId("T1")).and(AnImmediateTransition.withId("T2")).		
    		and(AnExternalTransition.withId("T3").andExternalClass("edu.berkeley.icsi.xschema.TestingCloseExternalTransition")).
    		and(ANormalArc.withSource("P0").andTarget("T0").with("1", "Default").token()).
    		and(ANormalArc.withSource("T0").andTarget("P1").with("1", "Default").token()).
    		and(ANormalArc.withSource("P1").andTarget("T1").with("1", "Default").token()).
    		and(ANormalArc.withSource("T1").andTarget("P2").with("1", "Default").token()).
    		and(ANormalArc.withSource("P2").andTarget("T2").with("1", "Default").token()).
    		and(ANormalArc.withSource("T2").andTarget("P3").with("1", "Default").token()).
    		and(ANormalArc.withSource("T1").andTarget("P4").with("1", "Default").token()).
    		and(ANormalArc.withSource("P4").andTarget("T3").with("1", "Default").token()).
    		and(ANormalArc.withSource("T3").andTarget("P5").with("1", "Default").token()).
    		and(ANormalArc.withSource("P5").andTarget("T2").with("1", "Default").token()).
    		andFinally(ANormalArc.withSource("P6").andTarget("T2").with("1", "Default").token());
    	name(net, Place.class, "P0", "Enabled"); 
    	name(net, Place.class, "P1", "Ready"); 
    	name(net, Place.class, "P2", "Ongoing"); 
    	name(net, Place.class, "P3", "Done"); 
    	name(net, Place.class, "P5", "Closing"); 
    	name(net, Place.class, "P6", "Close_sensed"); 
    	name(net, Transition.class, "T0", "Prepare"); 
    	name(net, Transition.class, "T1", "Start"); 
    	name(net, Transition.class, "T2", "Finish"); 
    	name(net, Transition.class, "T3", "Close"); 
//    	for (Place place : net.getExecutablePetriNet().getPlaces()) {
//			System.out.println(place.getId());
//			System.out.println(place.getName());
//		}
    	return net; 
    }

	@SuppressWarnings("unchecked")
	private void name(PetriNet net, @SuppressWarnings("rawtypes") Class clazz, String component, String name) {
		// rename components manually, until support added to DSL in PIPECore
		// works after a fashion, but arcs are inconsistently named
		try {
			net.getComponent(component, clazz).setId(name);
		} catch (PetriNetComponentNotFoundException e) {
			e.printStackTrace();
		}
		
	}

	public void closeExternalTransitionFired() {
		try {
			if (closeSensed) {
				runner.markPlace(CLOSE_SENSED, "Default", 1);
			} else {
				runner.markPlace("Grasp.Close_hand.Missed", "Default", 1);
			}
		} catch (InterfaceException e) {
			e.printStackTrace();
		}
	}
	private void cleanupFile(String filename) {
		file = new File(filename); 
		if (file.exists()) file.delete();
	}

	@SuppressWarnings("unchecked")
	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (evt.getPropertyName().equals(Place.TOKEN_CHANGE_MESSAGE)) {
			tokenEvent = true; 
			Place place = (Place) evt.getSource(); 
			assertEquals("Grasp.Suspended", place.getId()); 
			Map<String, Integer> token = (Map<String, Integer>) evt.getNewValue(); 
			assertEquals(1, token.size());
			Entry<String, Integer> entry = token.entrySet().iterator().next(); 
			assertEquals("Default", entry.getKey());
			assertEquals(1, entry.getValue().intValue());
		}
	}
}
