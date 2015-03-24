package edu.berkeley.icsi.xschema;

import uk.ac.imperial.pipe.models.petrinet.AbstractExternalTransition;

public class TestingCloseExternalTransition extends AbstractExternalTransition {

	@Override
	public void fire() {
//		System.out.println("TestingCloseExternalTransition firing.");
		
		((GraspExampleTest)  context).closeExternalTransitionFired(); 
	}
	
}
