package ftnTheory;

import java.awt.Color;
import java.io.BufferedWriter;
import java.io.File;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;
import java.util.Vector;

import allMains.CPBase;
import allMains.CirclePack;
import combinatorics.komplex.HalfEdge;
import combinatorics.komplex.RedEdge;
import complex.Complex;
import exceptions.InOutException;
import exceptions.ParserException;
import geometry.CircleSimple;
import geometry.EuclMath;
import input.CPFileManager;
import komplex.EdgeSimple;
import listManip.EdgeLink;
import listManip.FaceLink;
import listManip.NodeLink;
import packing.PackData;
import packing.PackExtender;
import util.CmdStruct;
import util.ColorUtil;
import util.DispFlags;
import util.StringUtil;
import util.TriAspect;

/**
 * This extender was started to study affine structures
 * on tori, but with advent of DCEL structures most of
 * functionality has been moved to 'ProjStruct'; e.g. see
 * 'affinepack'.
 * 
 * This class also contains legacy code for experiments on
 * "weak consistency", which I don't quite recall now: 
 * In a geometric triangulation, each triangle can be 
 * formed by a tangent triple of circles. If the tangency 
 * points for the triangles on either side of a shared edge 
 * agree, then we call that weak consistency --- its 
 * weaker than when the triangulation is formed by a circle
 * packing.
 * 
 * @authors, Chris Sass and Ken Stephenson
 */
public class AffinePack extends PackExtender {
	public Random rand;
	public TriAspect []aspects;
	public static double TOLER=.00000001;
	public static double OKERR=.0000000001; 
	public static int PASSES=10000;
	
	// Constructor
	public AffinePack(PackData p) {
		super(p);
		extensionType="AFFINEPACK";
		extensionAbbrev="AP";
		toolTip="'AffinePack' provides for face-based studies of "+
			"euclidean triangulations";
		registerXType();
		int rslt;
		try {
			rslt=cpCommand(packData,"geom_to_e");
		} catch(Exception ex) {
			rslt=0;
		}
		if (rslt==0) {
			errorMsg("AP: failed to convert to euclidean");
			running=false;
		}
		if (packData.getBdryCompCount()>0 || packData.genus!=1) {
			errorMsg("AP: failed, packing is not a torus");
			running=false;
		}
		if (running) {
			resetAspects();
			packData.packExtensions.add(this);
			rand=new Random(0); // seed for debugging
		}
	}
	
	/**
	 * Create a new 'aspects' array of 'TriAspect's. May need to do 
	 * this if the packing or its drawing order is changed.
	 */
	public void resetAspects() {
		// create vector of 'TriAspect's, one for each face
		aspects=new TriAspect[packData.faceCount+1];
		for (int f=1;f<=packData.faceCount;f++) {
			aspects[f]=new TriAspect(packData.hes);
			TriAspect tas=aspects[f];
			tas.baseEdge=pdc.faces[f].edge;
			tas.faceIndx=tas.baseEdge.face.faceIndx;
			int[] verts=packData.packDCEL.faces[f].getVerts();
			for (int j=0;j<3;j++) {
				int v=verts[j];
				tas.vert[j]=v;
				// set 'labels'
				tas.labels[j]=packData.getRadius(v);
				// set 'centers'
				tas.setCenter(new Complex(packData.getCenter(v)),j);
			}
			// set 'sides' from 'center's
			tas.centers2Sides();
		}
	}
	
	/*
	 *    EDGE12
	 */
	public static double [] Edge12(PackData p, TriAspect[] asp){
		
		double EL=0.0;
		double L=0.0;
		
		ProjStruct.setEffective(p,asp);
		
		NodeLink vlist = null;
		vlist=new NodeLink(p,"b(1,2)");
		Iterator<Integer> bdry=vlist.iterator();
		
		while (bdry.hasNext()){
			
			int bd=bdry.next();
			
			if (bd==1){
				EL+=p.getRadius(1);
			}
			
			else if (bd==2){
				EL+=p.getRadius(2);
			}
			
			else EL+=2*p.getRadius(bd);
	
		}
		
		EdgeLink elist = null;
		elist=new EdgeLink(p,"b(1,2)");
		Iterator<EdgeSimple> bded=elist.iterator();
		
		while (bded.hasNext()){
			
		EdgeSimple ed=bded.next();
		
		int [] fa = p.left_face(ed);
		int f=fa[0];
		
		int w1=asp[f].vertIndex(ed.v);
		L+=asp[f].labels[w1];
		
		int w2=asp[f].vertIndex(ed.w);
		L+=asp[f].labels[w2];
			
		}
		
		double []info = new double [4];
		info[0]=(double) vlist.size();
		info[1]=EL;
		info[2]=(double) elist.size();
		info[3]=L;
		return info;
		
	}
	
	
	/*
	 *    EDGE34
	 */
	public static double [] Edge34(PackData p, TriAspect[] asp){
		
		double EL=0.0;
		double L=0.0;
		
		ProjStruct.setEffective(p,asp);
		
		NodeLink vlist = null;
		vlist=new NodeLink(p,"b(3,4)");
		Iterator<Integer> bdry=vlist.iterator();
		
		while (bdry.hasNext()){
			
			int bd=bdry.next();
			
			if (bd==3){
				EL+=p.getRadius(3);
			}
			
			else if (bd==4){
				EL+=p.getRadius(4);
			}
			
			else EL+=2*p.getRadius(bd);
	
		}
		
		EdgeLink elist = null;
		elist=new EdgeLink(p,"b(3,4)");
		Iterator<EdgeSimple> bded=elist.iterator();
		
		while (bded.hasNext()){
			
		EdgeSimple ed=bded.next();
		
		int [] fa = p.left_face(ed);
		int f=fa[0];
		
		int w1=asp[f].vertIndex(ed.v);
		L+=asp[f].labels[w1];
		
		int w2=asp[f].vertIndex(ed.w);
		L+=asp[f].labels[w2];
			
		}
		
		double []info = new double [4];
		info[0]=(double) vlist.size();
		info[1]=EL;
		info[2]=(double) elist.size();
		info[3]=L;
		return info;
		
	}
	
	/*
	 *    EDGE23
	 */
	public static double [] Edge23(PackData p, TriAspect[] asp){
		
		double EL=0.0;
		double L=0.0;
		
		ProjStruct.setEffective(p,asp);
		
		NodeLink vlist = null;
		vlist=new NodeLink(p,"b(2,3)");
		Iterator<Integer> bdry=vlist.iterator();
		
		while (bdry.hasNext()){
			
			int bd=bdry.next();
			
			if (bd==2){
				EL+=p.getRadius(2);
			}
			
			else if (bd==3){
				EL+=p.getRadius(3);
			}
			
			else EL+=2*p.getRadius(bd);
	
		}
		
		EdgeLink elist = null;
		elist=new EdgeLink(p,"b(2,3)");
		Iterator<EdgeSimple> bded=elist.iterator();
		
		while (bded.hasNext()){
			
		EdgeSimple ed=bded.next();
		
		int [] fa = p.left_face(ed);
		int f=fa[0];
		
		int w1=asp[f].vertIndex(ed.v);
		L+=asp[f].labels[w1];
		
		int w2=asp[f].vertIndex(ed.w);
		L+=asp[f].labels[w2];
			
		}
		
		double []info = new double [4];
		info[0]=(double) vlist.size();
		info[1]=EL;
		info[2]=(double) elist.size();
		info[3]=L;
		return info;
		
	}
	
	/*
	 *    EDGE41
	 */
	public static double [] Edge41(PackData p, TriAspect[] asp){
		
		double EL=0.0;
		double L=0.0;
		
		ProjStruct.setEffective(p,asp);
		
		NodeLink vlist = null;
		vlist=new NodeLink(p,"b(4,1)");
		Iterator<Integer> bdry=vlist.iterator();
		
		while (bdry.hasNext()){
			
			int bd=bdry.next();
			
			if (bd==4){
				EL+=p.getRadius(4);
			}
			
			else if (bd==1){
				EL+=p.getRadius(1);
			}
			
			else EL+=2*p.getRadius(bd);
	
		}
		
		EdgeLink elist = null;
		elist=new EdgeLink(p,"b(4,1)");
		Iterator<EdgeSimple> bded=elist.iterator();
		
		while (bded.hasNext()){
			
		EdgeSimple ed=bded.next();
		
		int [] fa = p.left_face(ed);
		int f=fa[0];
		
		int w1=asp[f].vertIndex(ed.v);
		L+=asp[f].labels[w1];
		
		int w2=asp[f].vertIndex(ed.w);
		L+=asp[f].labels[w2];
			
		}
		
		double []info = new double [4];
		info[0]=(double) vlist.size();
		info[1]=EL;
		info[2]=(double) elist.size();
		info[3]=L;
		return info;
		
	}
	
	/*
	 *    ADJBD
	 */
	public static int AdjBd(PackData p, TriAspect[] asp){
		
		ProjStruct.setEffective(p,asp);
		
		//edge12
		
		double EL12=0.0;
		double L12=0.0;
		
		NodeLink vlist = null;
		vlist=new NodeLink(p,"b(1,2)");
		Iterator<Integer> bdry=vlist.iterator();
		
		while (bdry.hasNext()){
			
			int bd=bdry.next();
			
			if (bd==1){
				EL12+=p.getRadius(1);
			}
			
			else if (bd==2){
				EL12+=p.getRadius(2);
			}
			
			else EL12+=2*p.getRadius(bd);
	
		}
		
		int n=vlist.size()-1;
		
		EdgeLink elist = null;
		elist=new EdgeLink(p,"b(1,2)");
		Iterator<EdgeSimple> bded=elist.iterator();
		
		while (bded.hasNext()){
			
		EdgeSimple ed=bded.next();
		
		int [] fa = p.left_face(ed);
		int f=fa[0];
		
		int w1=asp[f].vertIndex(ed.v);
		L12+=asp[f].labels[w1];
		
		int w2=asp[f].vertIndex(ed.w);
		L12+=asp[f].labels[w2];
			
		}
		
		int F1=p.getFaceFlower(1,0);
		int V1=asp[F1].vertIndex(1);
		Complex z1 = asp[F1].getCenter(V1);
		int F2=p.getFaceFlower(2,0);
		int V2=asp[F2].vertIndex(2);
		Complex z2 = asp[F2].getCenter(V2);
		
		if (EL12 <= L12){
			
			double G=L12-EL12;
			double N=(double) n;
			double g=G/N;
			
			elist = null;
			elist=new EdgeLink(p,"b(1,2)");
			bded=elist.iterator();
			
			while (bded.hasNext()){
				
				EdgeSimple ed=bded.next();
				
				int [] fa = p.left_face(ed);
				int f=fa[0];
				
				int v=ed.v;
				int w=ed.w;
				int w1=asp[f].vertIndex(v);
				
				Complex Z = asp[f].getCenter(w1);
				Complex T = Z.minus(z1).divide(z2.minus(z1));
				double t=L12*T.x;
				
				double s = p.getRadius(v)+g+p.getRadius(w)+t;
				Complex S = z2.times(s/L12).add(z1.times(1.0-(s/L12)));
				int[] faceFlower=p.getFaceFlower(w);
				for (int j=0;j<p.countFaces(w);j++){
					int fc=faceFlower[j];
					int W=asp[fc].vertIndex(w);
					asp[fc].setCenter(S,W);
					asp[fc].centers2Labels();
				}
				
			}
			
		}
		else {
			
			int count = 0;
			
			vlist = null;
			vlist=new NodeLink(p,"b(1,2)");
			bdry=vlist.iterator();
			
			while (bdry.hasNext()){
				
				int w=bdry.next();
				
				double N=(double) n;
				double s = (count*L12)/N;
				Complex S = z2.times(s/L12).add(z1.times(1.0-(s/L12)));
				int[] faceFlower=p.getFaceFlower(w);
				for (int j=0;j<p.countFaces(w);j++){
					int fc=faceFlower[j];
					int W=asp[fc].vertIndex(w);
					asp[fc].setCenter(S,W);
					asp[fc].centers2Labels();
				}
				
				count++;
				
			}
			
		}
		
		//edge34
		
		double EL34=0.0;
		double L34=0.0;
		
		vlist = null;
		vlist=new NodeLink(p,"b(3,4)");
		bdry=vlist.iterator();
		
		while (bdry.hasNext()){
			
			int bd=bdry.next();
			
			if (bd==3){
				EL34+=p.getRadius(3);
			}
			
			else if (bd==4){
				EL34+=p.getRadius(4);
			}
			
			else EL34+=2*p.getRadius(bd);
	
		}
		
		n=vlist.size()-1;
		
		elist = null;
		elist=new EdgeLink(p,"b(3,4)");
		bded=elist.iterator();
		
		while (bded.hasNext()){
			
		EdgeSimple ed=bded.next();
		
		int [] fa = p.left_face(ed);
		int f=fa[0];
		
		int w1=asp[f].vertIndex(ed.v);
		L34+=asp[f].labels[w1];
		
		int w2=asp[f].vertIndex(ed.w);
		L34+=asp[f].labels[w2];
			
		}
		
		int F3=p.getFaceFlower(3,0);
		int V3=asp[F3].vertIndex(3);
		Complex z3 = asp[F3].getCenter(V3);
		int F4=p.getFaceFlower(4,0);
		int V4=asp[F4].vertIndex(4);
		Complex z4 = asp[F4].getCenter(V4);
		
		if (EL34 <= L34){
			
			double G=L34-EL34;
			double N=(double) n;
			double g=G/N;
			
			elist = null;
			elist=new EdgeLink(p,"b(3,4)");
			bded=elist.iterator();
			
			while (bded.hasNext()){
				
				EdgeSimple ed=bded.next();
				
				int [] fa = p.left_face(ed);
				int f=fa[0];
				
				int v=ed.v;
				int w=ed.w;
				int w1=asp[f].vertIndex(v);
				
				Complex Z = asp[f].getCenter(w1);
				Complex T = Z.minus(z3).divide(z4.minus(z3));
				double t=L34*T.x;
				
				double s = p.getRadius(v)+g+p.getRadius(w)+t;
				Complex S = z4.times(s/L34).add(z3.times(1.0-(s/L34)));
				int[] faceFlower=p.getFaceFlower(w);
				for (int j=0;j<p.countFaces(w);j++){
					int fc=faceFlower[j];
					int W=asp[fc].vertIndex(w);
					asp[fc].setCenter(S,W);
					asp[fc].centers2Labels();
					
				}
				
			}
			
		}
		
		else {
			
			int count = 0;
			
			vlist = null;
			vlist=new NodeLink(p,"b(3,4)");
			bdry=vlist.iterator();
			
			while (bdry.hasNext()){
				
				int w=bdry.next();
				
				double N=(double) n;
				double s = (count*L34)/N;
				Complex S = z4.times(s/L34).add(z3.times(1.0-(s/L34)));
				int[] faceFlower=p.getFaceFlower(w);
				for (int j=0;j<p.countFaces(w);j++){
					int fc=faceFlower[j];
					int W=asp[fc].vertIndex(w);
					asp[fc].setCenter(S,W);
					asp[fc].centers2Labels();
				}
				
				count++;
				
			}
			
		}
		
		//edge23
		
		double EL23=0.0;
		double L23=0.0;
		
		vlist = null;
		vlist=new NodeLink(p,"b(2,3)");
		bdry=vlist.iterator();
		
		while (bdry.hasNext()){
			
			int bd=bdry.next();
			
			if (bd==2){
				EL23+=p.getRadius(2);
			}
			
			else if (bd==3){
				EL23+=p.getRadius(3);
			}
			
			else EL23+=2*p.getRadius(bd);
	
		}
		
		n=vlist.size()-1;
		
		elist = null;
		elist=new EdgeLink(p,"b(2,3)");
		bded=elist.iterator();
		
		while (bded.hasNext()){
			
		EdgeSimple ed=bded.next();
		
		int [] fa = p.left_face(ed);
		int f=fa[0];
		
		int w1=asp[f].vertIndex(ed.v);
		L23+=asp[f].labels[w1];
		
		int w2=asp[f].vertIndex(ed.w);
		L23+=asp[f].labels[w2];
			
		}
		
		F2=p.getFaceFlower(2,0);
		V2=asp[F2].vertIndex(2);
		z2 = asp[F2].getCenter(V2);
		F3=p.getFaceFlower(3,0);
		V3=asp[F3].vertIndex(3);
		z3 = asp[F3].getCenter(V3);
		
		if (EL23 <= L23){
			
			double G=L23-EL23;
			double N=(double) n;
			double g=G/N;
			
			elist = null;
			elist=new EdgeLink(p,"b(2,3)");
			bded=elist.iterator();
			
			while (bded.hasNext()){
				
				EdgeSimple ed=bded.next();
				
				int [] fa = p.left_face(ed);
				int f=fa[0];
				
				int v=ed.v;
				int w=ed.w;
				int w1=asp[f].vertIndex(v);
				
				Complex Z = asp[f].getCenter(w1);
				Complex T = Z.minus(z2).divide(z3.minus(z2));
				double t=L23*T.x;
				
				double s = p.getRadius(v)+g+p.getRadius(w)+t;
				Complex S = z3.times(s/L23).add(z2.times(1.0-(s/L23)));
				int[] faceFlower=p.getFaceFlower(w);
				for (int j=0;j<p.countFaces(w);j++){
					int fc=faceFlower[j];
					int W=asp[fc].vertIndex(w);
					asp[fc].setCenter(S,W);
					asp[fc].centers2Labels();
					
				}
				
			}
			
		}
		
		else {
			
			int count = 0;
			
			vlist = null;
			vlist=new NodeLink(p,"b(2,3)");
			bdry=vlist.iterator();
			
			while (bdry.hasNext()){
				
				int w=bdry.next();
				
				double N=(double) n;
				double s = (count*L23)/N;
				Complex S = z3.times(s/L23).add(z2.times(1.0-(s/L23)));
				int[] faceFlower=p.getFaceFlower(w);
				for (int j=0;j<p.countFaces(w);j++){
					int fc=faceFlower[j];
					int W=asp[fc].vertIndex(w);
					asp[fc].setCenter(S,W);
					asp[fc].centers2Labels();
				}
				
				count++;
				
			}
			
		}
		
		//edge41
		
		double EL41=0.0;
		double L41=0.0;
		
		vlist = null;
		vlist=new NodeLink(p,"b(4,1)");
		bdry=vlist.iterator();
		
		while (bdry.hasNext()){
			
			int bd=bdry.next();
			
			if (bd==4){
				EL41+=p.getRadius(4);
			}
			
			else if (bd==1){
				EL41+=p.getRadius(1);
			}
			
			else EL41+=2*p.getRadius(bd);
	
		}
		
		n=vlist.size()-1;
		
		elist = null;
		elist=new EdgeLink(p,"b(4,1)");
		bded=elist.iterator();
		
		while (bded.hasNext()){
			
		EdgeSimple ed=bded.next();
		
		int [] fa = p.left_face(ed);
		int f=fa[0];
		
		int w1=asp[f].vertIndex(ed.v);
		L41+=asp[f].labels[w1];
		
		int w2=asp[f].vertIndex(ed.w);
		L41+=asp[f].labels[w2];
			
		}
		
		F4=p.getFaceFlower(4,0);
		V4=asp[F4].vertIndex(4);
		z4 = asp[F4].getCenter(V4);
		F1=p.getFaceFlower(1,0);
		V1=asp[F1].vertIndex(1);
		z1 = asp[F1].getCenter(V1);
		
		if (EL41 <= L41){
			
			double G=L41-EL41;
			double N=(double) n;
			double g=G/N;
			
			elist = null;
			elist=new EdgeLink(p,"b(4,1)");
			bded=elist.iterator();
			
			while (bded.hasNext()){
				
				EdgeSimple ed=bded.next();
				
				int [] fa = p.left_face(ed);
				int f=fa[0];
				
				int v=ed.v;
				int w=ed.w;
				int w1=asp[f].vertIndex(v);
				
				Complex Z = asp[f].getCenter(w1);
				Complex T = Z.minus(z4).divide(z1.minus(z4));
				double t=L41*T.x;
				
				double s = p.getRadius(v)+g+p.getRadius(w)+t;
				Complex S = z1.times(s/L41).add(z4.times(1.0-(s/L41)));
				int[] faceFlower=p.getFaceFlower(w);
				for (int j=0;j<p.countFaces(w);j++){
					int fc=faceFlower[j];
					int W=asp[fc].vertIndex(w);
					asp[fc].setCenter(S,W);
					asp[fc].centers2Labels();
					
				}
				
			}
			
		}
		
		else {
			
			int count = 0;
			
			vlist = null;
			vlist=new NodeLink(p,"b(4,1)");
			bdry=vlist.iterator();
			
			while (bdry.hasNext()){
				
				int w=bdry.next();
				
				double N=(double) n;
				double s = (count*L41)/N;
				Complex S = z1.times(s/L41).add(z4.times(1.0-(s/L41)));
				int[] faceFlower=p.getFaceFlower(w);
				for (int j=0;j<p.countFaces(w);j++){
					int fc=faceFlower[j];
					int W=asp[fc].vertIndex(w);
					asp[fc].setCenter(S,W);
					asp[fc].centers2Labels();
				}
				
				count++;
				
			}
			
		}
		
		return 1;
		
	}
	
	
	/*
	 *    ADJBD
	 *    
	 *    Assume the centers have been placed for a triangulation 
	 *    and stored in PackData
	 *    
	 */
	public static int adjBd(PackData p, TriAspect[] asp){
		
		ProjStruct.setEffective(p,asp);
		
		//edge12
		
		double EL12=0.0;
		double L12=0.0;
		
		NodeLink vlist = null;
		vlist=new NodeLink(p,"b(1,2)");
		Iterator<Integer> bdry=vlist.iterator();
		
		while (bdry.hasNext()){
			
			int bd=bdry.next();
			
			if (bd==1){
				EL12+=p.getRadius(1);
			}
			
			else if (bd==2){
				EL12+=p.getRadius(2);
			}
			
			else EL12+=2*p.getRadius(bd);
	
		}
		
		EdgeLink elist = null;
		elist=new EdgeLink(p,"b(1,2)");
		Iterator<EdgeSimple> bded=elist.iterator();
		
		while (bded.hasNext()){
			
		EdgeSimple ed=bded.next();
		
		int [] fa = p.left_face(ed);
		int f=fa[0];
		
		int w1=asp[f].vertIndex(ed.v);
		L12+=asp[f].labels[w1];
		
		int w2=asp[f].vertIndex(ed.w);
		L12+=asp[f].labels[w2];
			
		}
		
		int F1=p.getFaceFlower(1,0);
		int V1=asp[F1].vertIndex(1);
		Complex z1 = asp[F1].getCenter(V1);
		int F2=p.getFaceFlower(2,0);
		int V2=asp[F2].vertIndex(2);
		Complex z2 = asp[F2].getCenter(V2);
		
		elist = null;
		elist=new EdgeLink(p,"b(1,2)");
		bded=elist.iterator();
		
		while (bded.hasNext()){
			
			EdgeSimple ed=bded.next();
			
			int [] fa = p.left_face(ed);
			int f=fa[0];
			
			int v=ed.v;
			int w=ed.w;
			int vv=asp[f].vertIndex(v);
			
			Complex Z = asp[f].getCenter(vv);
			Complex T = Z.minus(z1).divide(z2.minus(z1));
			double t=L12*T.x;
			
			double s = t+((L12/EL12)*(p.getRadius(v)+p.getRadius(w)));
			Complex S = z2.times(s/L12).add(z1.times(1.0-(s/L12)));
			int[] faceFlower=p.getFaceFlower(w);
			for (int j=0;j<p.countFaces(w);j++){
				int fc=faceFlower[j];
				int W=asp[fc].vertIndex(w);
				asp[fc].setCenter(S,W);
				asp[fc].centers2Labels();
				
			}
			
		}
		
		//edge34
		
		double EL34=0.0;
		double L34=0.0;
		
		vlist = null;
		vlist=new NodeLink(p,"b(3,4)");
		bdry=vlist.iterator();
		
		while (bdry.hasNext()){
			
			int bd=bdry.next();
			
			if (bd==3){
				EL34+=p.getRadius(3);
			}
			
			else if (bd==4){
				EL34+=p.getRadius(4);
			}
			
			else EL34+=2*p.getRadius(bd);
	
		}
		
		elist = null;
		elist=new EdgeLink(p,"b(3,4)");
		bded=elist.iterator();
		
		while (bded.hasNext()){
			
		EdgeSimple ed=bded.next();
		
		int [] fa = p.left_face(ed);
		int f=fa[0];
		
		int w1=asp[f].vertIndex(ed.v);
		L34+=asp[f].labels[w1];
		
		int w2=asp[f].vertIndex(ed.w);
		L34+=asp[f].labels[w2];
			
		}
		
		int F3=p.getFaceFlower(3,0);
		int V3=asp[F3].vertIndex(3);
		Complex z3 = asp[F3].getCenter(V3);
		int F4=p.getFaceFlower(4,0);
		int V4=asp[F4].vertIndex(4);
		Complex z4 = asp[F4].getCenter(V4);
		
		elist = null;
		elist=new EdgeLink(p,"b(3,4)");
		bded=elist.iterator();
		
		while (bded.hasNext()){
			
			EdgeSimple ed=bded.next();
			
			int [] fa = p.left_face(ed);
			int f=fa[0];
			
			int v=ed.v;
			int w=ed.w;
			int vv=asp[f].vertIndex(v);
			
			Complex Z = asp[f].getCenter(vv);
			Complex T = Z.minus(z3).divide(z4.minus(z3));
			double t=L34*T.x;
			
			double s = t+((L34/EL34)*(p.getRadius(v)+p.getRadius(w)));
			Complex S = z4.times(s/L34).add(z3.times(1.0-(s/L34)));
			int[] faceFlower=p.getFaceFlower(w);
			for (int j=0;j<p.countFaces(w);j++){
				int fc=faceFlower[j];
				int W=asp[fc].vertIndex(w);
				asp[fc].setCenter(S,W);
				asp[fc].centers2Labels();
				
			}
			
		}
		
		//edge23
		
		double EL23=0.0;
		double L23=0.0;
		
		vlist = null;
		vlist=new NodeLink(p,"b(2,3)");
		bdry=vlist.iterator();
		
		while (bdry.hasNext()){
			
			int bd=bdry.next();
			
			if (bd==2){
				EL23+=p.getRadius(2);
			}
			
			else if (bd==3){
				EL23+=p.getRadius(3);
			}
			
			else EL23+=2*p.getRadius(bd);
	
		}
		
		elist = null;
		elist=new EdgeLink(p,"b(2,3)");
		bded=elist.iterator();
		
		while (bded.hasNext()){
			
		EdgeSimple ed=bded.next();
		
		int [] fa = p.left_face(ed);
		int f=fa[0];
		
		int w1=asp[f].vertIndex(ed.v);
		L23+=asp[f].labels[w1];
		
		int w2=asp[f].vertIndex(ed.w);
		L23+=asp[f].labels[w2];
			
		}
		
		F2=p.getFaceFlower(2,0);
		V2=asp[F2].vertIndex(2);
		z2 = asp[F2].getCenter(V2);
		F3=p.getFaceFlower(3,0);
		V3=asp[F3].vertIndex(3);
		z3 = asp[F3].getCenter(V3);
		
		elist = null;
		elist=new EdgeLink(p,"b(2,3)");
		bded=elist.iterator();
		
		while (bded.hasNext()){
			
			EdgeSimple ed=bded.next();
			
			int [] fa = p.left_face(ed);
			int f=fa[0];
			
			int v=ed.v;
			int w=ed.w;
			int vv=asp[f].vertIndex(v);
			
			Complex Z = asp[f].getCenter(vv);
			Complex T = Z.minus(z2).divide(z3.minus(z2));
			double t=L23*T.x;
			
			double s = t+((L23/EL23)*(p.getRadius(v)+p.getRadius(w)));
			Complex S = z3.times(s/L23).add(z2.times(1.0-(s/L23)));
			int[] faceFlower=p.getFaceFlower(w);
			for (int j=0;j<p.countFaces(w);j++){
				int fc=faceFlower[j];
				int W=asp[fc].vertIndex(w);
				asp[fc].setCenter(S,W);
				asp[fc].centers2Labels();
				
			}
			
		}
		
		//edge41
		
		double EL41=0.0;
		double L41=0.0;
		
		vlist = null;
		vlist=new NodeLink(p,"b(4,1)");
		bdry=vlist.iterator();
		
		while (bdry.hasNext()){
			
			int bd=bdry.next();
			
			if (bd==4){
				EL41+=p.getRadius(4);
			}
			
			else if (bd==1){
				EL41+=p.getRadius(1);
			}
			
			else EL41+=2*p.getRadius(bd);
	
		}
		
		elist = null;
		elist=new EdgeLink(p,"b(4,1)");
		bded=elist.iterator();
		
		while (bded.hasNext()){
			
		EdgeSimple ed=bded.next();
		
		int [] fa = p.left_face(ed);
		int f=fa[0];
		
		int w1=asp[f].vertIndex(ed.v);
		L41+=asp[f].labels[w1];
		
		int w2=asp[f].vertIndex(ed.w);
		L41+=asp[f].labels[w2];
			
		}
		
		F4=p.getFaceFlower(4,0);
		V4=asp[F4].vertIndex(4);
		z4 = asp[F4].getCenter(V4);
		F1=p.getFaceFlower(1,0);
		V1=asp[F1].vertIndex(1);
		z1 = asp[F1].getCenter(V1);
		
		elist = null;
		elist=new EdgeLink(p,"b(4,1)");
		bded=elist.iterator();
		
		while (bded.hasNext()){
			
			EdgeSimple ed=bded.next();
			
			int [] fa = p.left_face(ed);
			int f=fa[0];
			
			int v=ed.v;
			int w=ed.w;
			int vv=asp[f].vertIndex(v);
			
			Complex Z = asp[f].getCenter(vv);
			Complex T = Z.minus(z4).divide(z1.minus(z4));
			double t=L41*T.x;
			
			double s = t+((L41/EL41)*(p.getRadius(v)+p.getRadius(w)));
			Complex S = z1.times(s/L41).add(z4.times(1.0-(s/L41)));
			int[] faceFlower=p.getFaceFlower(w);
			for (int j=0;j<p.countFaces(w);j++){
				int fc=faceFlower[j];
				int W=asp[fc].vertIndex(w);
				asp[fc].setCenter(S,W);
				asp[fc].centers2Labels();
				
			}
			
		}
		
		return 1;
		
	}
	
	/*
	 *    NEW CENTER
	 *    
	 *    Assume the centers have been placed for a triangulation and 
	 *    stored in PackData
	 *    
	 *    @param v (interior vertex)
	 *    @return new center nz (Complex)
	 */
	public static Complex NewCenter(PackData p, TriAspect[] asp, int v){
		
		CircleSimple sc=new CircleSimple();
		double x[] = new double[p.countFaces(v)];
		double y[] = new double[p.countFaces(v)];
		double nx=0.0;
		double ny=0.0;
		Complex z;
		Complex nz;
		
		int[] faceFlower=p.getFaceFlower(v);
		for (int j=0;j<p.countFaces(v);j++){
			int[] verts=p.packDCEL.faces[faceFlower[j]].getVerts();
			sc=EuclMath.eucl_tri_incircle(p.getCenter(verts[0]),
					p.getCenter(verts[1]),p.getCenter(verts[2]));
			z=sc.center;
			x[j]=z.x;
			y[j]=z.y;
			
		}
		
		for (int j=0;j<p.countFaces(v);j++){
			
			nx+=x[j];
			ny+=y[j];
			
		}
		
		nx=nx/p.countFaces(v);
		ny=ny/p.countFaces(v);
		nz=new Complex(nx,ny);
		
		return nz;
		
	}
	
	
	/*
	 *    IncNC (Incremental New Center)
	 *    
	 */
	public int IncNC(PackData p, TriAspect[] asp){
		
		int count=0;
		int N=0;
		int C=0;
		int r=0;
		int s=0;
		double err=0.0;
		double E=0.0;
		
		// count interior vertices
		
		for (int j=1;j<=p.nodeCount;j++){
			if (!p.isBdry(j)) {
				count++;
			}
		}
		
		// create array of interior vertices
		
		int vert[] = new int [count];
		
		for (int j=1;j<=p.nodeCount;j++){
			if (!p.isBdry(j)) {
				vert[N]=j;
				N++;
			}
		}
		
		// compute initial error E
		for (int v=1;v<=p.nodeCount;v++) {
			int[] petals=p.getPetals(v);
			for (int j=0;j<petals.length;j++) {
				int w=petals[j];
				if (w>v) {
					double prd=Math.log(Math.abs(edgeRatioError(p,new EdgeSimple(v,w),asp)));
					E += prd*prd;
				}
			}
		}
		
		// adjust random centers until 5% decrease in error
		
		err=E;
		
		while ((C<50) && ((err/E)>0.95)){
			
			err=0.0;
			s = (int)(Math.floor(count*(rand.nextDouble())));
			r=vert[s];
			
			p.setCenter(r,NewCenter(p,asp,r));
			int[] faceFlower=p.getFaceFlower(r);
			for (int j=0;j<p.countFaces(r);j++){
				int f=faceFlower[j];
				int w=asp[f].vertIndex(r);
				asp[f].setCenter(NewCenter(p,asp,r),w);
				asp[f].centers2Labels();
				
			}
			
			for (int v=1;v<=p.nodeCount;v++) {
				int[] petals=p.getPetals(v);
				for (int j=0;j<petals.length;j++) {
					int w=petals[j];
					if (w>v) {
						double prd=Math.log(Math.abs(edgeRatioError(p,new EdgeSimple(v,w),asp)));
						err += prd*prd;
					}
				}
			}
			
			C++;
			
		}
		
		return C;
		
	}

	
	/**
	 *   RECTANGLE ADJUST
	 */
	public int[] rectAdjust(PackData p, TriAspect[] aspts,int passes){
		int count=0;
		int num=0;
		int N=0;
		int M=0;
		double err=0.0;
		double t=0.0;
		double F=0.0;
		double eps=0.0;
		
		// count interior edges
		for (int v=1;v<=p.nodeCount;v++) {
			int[] petals=p.getPetals(v);
			for (int j=0;j<petals.length;j++) {
				int w=petals[j];
				if ((!p.isBdry(v) || !p.isBdry(w)) && w>v){
					num++;
				}
			}
		}
		
		// create list of edge first vertices (e) and second vertices (ee)
		int[] e = new int[num+1];
		int[] ee = new int[num+1];
		int C=1;
		
		for (int v=1;v<=p.nodeCount;v++) {
			int[] petals=p.getPetals(v);
			for (int j=0;j<petals.length;j++) {
				int w=petals[j];
				if ((!p.isBdry(v) || !p.isBdry(w)) && w>v){
					e[C]=v;
					ee[C]=w;
					C++;
				}
			}
		}
		
		// compute initial SC (strong consistency) error
		for (int v=1;v<=num;v++){
			double prd=Math.abs(Math.log(edgeRatioError(p,new EdgeSimple(e[v],ee[v]),aspts)));
			err += prd*prd;
		}
		
		// set cutoff value
		double rec= 1.0 / num;
		double cut= err * rec;
		
		// cycle through adjustments
		while ((cut > TOLER && count < passes)){
			
			// F interpolates between 1.2 and 1
			F=1.2-((0.2/passes)*count);
//			F=10;
			
			for (int v=1;v<=num;v++){
				M++;
				double f=Math.abs(Math.log(edgeRatioError(p,new EdgeSimple(e[v],ee[v]),aspts)));
				if ((f*f)>(F*cut)){
					eps=((f*f)/cut)-1.0;
					t=eps/Math.exp(1.0+eps);
//					del=(f*f)/cut;
//					t=(2+del)/(10*del);
					// del>1 and 0.1<t<0.3
					edgeAdjust(p,new EdgeSimple (e[v],ee[v]),t,aspts);
					ProjStruct.sideRiffle(p, aspts, 20, null);
					N++;
					}
			}
			
			// reset 'labels' vector from 'sides'
			for (int k=1;k<=p.faceCount;k++){ 
				aspts[k].sides2Labels();
			}
			
			//calculate error
			err=0.0;
			for (int j=1;j<=num;j++){
				double prd=Math.abs(Math.log(edgeRatioError(p,new EdgeSimple(e[j],ee[j]),aspts)));
//				if (Double.isNaN(prd)) {
//					msg("NaN with "+e[j]+" and "+ee[j]);
//				}
				err += prd*prd;
			}
			cut = err*rec;
			count++;
		}
		int info[] = new int [3];
		info[0]=count;
		info[1]=N;
		info[2]=M;
		return info;
	}

	/**
	 *   rectAd1
	 *   chooses a random side to adjust
	 *   does not riffle to fix angle sums
	 */
	public int rectAd1(PackData p, TriAspect[] aspts){
		int num=0;
		int N=0;
		double err=0.0;
		
		// compute initial SC error
		for (int v=1;v<=p.nodeCount;v++) {
			int[] petals=p.getPetals(v);
			for (int j=0;j<petals.length;j++) {
				int w=petals[j];
				if (w>v) {
					double prd=Math.log(Math.abs(edgeRatioError(p,new EdgeSimple(v,w),aspts)));
					err += prd*prd;
				}
			}
		}
		
		//set cutoff value
		for (int v=1;v<=p.nodeCount;v++) {
			int[] petals=p.getPetals(v);
			for (int j=0;j<petals.length;j++) {
				int w=petals[j];
				if ((!p.isBdry(v) || !p.isBdry(w)) && w>v){
					num++;
				}
			}
		}
		
		double rec= 1.0 / num;
		double cut= err * rec;
		
		while (N<1){
		
			// choose random vertex
		int n = p.nodeCount;
		int r = (int)(Math.floor(1+n*rand.nextDouble()));
		
		// choose random edge
		int m = p.countFaces(r);
		int s = (int)(Math.floor(m*rand.nextDouble()));
		int w=p.getPetals(r)[s];
		
		if (!p.isBdry(r) || !p.isBdry(w)){
			double f=Math.log(Math.abs(edgeRatioError(p,new EdgeSimple(r,w),aspts)));
			if ((f*f)>cut){
				double eps=(f*f)/cut-1.0;
				double t=eps/Math.exp(1.0+eps);  
				edgeAdjust(p,new EdgeSimple (r,w) ,t,aspts);
				N=1;
				}
			}
		}
		
		return 1;
		
	}
	
	/** 
	 * riffle
	 * one pass of riffle
     */
	public static int riff(PackData p, TriAspect[] aspts) {
		int v;
		double verr, err;
		double[] curv = new double[p.nodeCount + 1];

		int aimNum = 0;
		int[] inDex = new int[p.nodeCount + 1];
		for (int vv = 1; vv <= p.nodeCount; vv++) {
			if (p.getAim(vv) > 0) {
				inDex[aimNum] = vv;
				aimNum++;
			}
		}
		if (aimNum == 0)
			return -1; // nothing to repack

		// compute initial curvatures
		for (int j = 0; j < aimNum; j++) {
			v = inDex[j];
			curv[v] = ProjStruct.angSumSide(p, v, 1.0,aspts);
		}

		// set cutoff value
		double accum = 0.0;
		for (int j = 0; j < aimNum; j++) {
			v = inDex[j];
			err = curv[v] - p.getAim(v);
			accum += (err < 0) ? (-err) : err;
		}
		double recip = .333333 / aimNum;
		double cut = accum * recip;

		// now cycle through adjustments --- riffle
			for (int j = 0; j < aimNum; j++) {
				v = inDex[j];
				curv[v] = ProjStruct.angSumSide(p, v,1.0,aspts);
				verr = curv[v] - p.getAim(v);

				// find/apply factor to radius or sides at v
				if (Math.abs(verr) > cut) {
					double sideFactor = ProjStruct.sideCalc(p,v, p.getAim(v), 5,
							aspts);
					ProjStruct.adjustSides(p,v, sideFactor,aspts);
					curv[v] = ProjStruct.angSumSide(p, v,1.0, aspts);
				}
			}

		return 1;
	}
	
	/** 
	 * edgeAdjust
	 * @param p ackData
	 * @param edge EdgeSimple
	 * @param t double, in (0,1)
	 * @param asp TriAspect[]
	 * @return 0 if edge is not interior, -1 on error
	 */
	
	//NEW VERSION
	public static int edgeAdjust(PackData p,EdgeSimple edge,
			double t,TriAspect []asp) {
		int f_right,f_left; // face on right/left (resp) of edge
		int j1; // index of v in face f1
		int j2; // index of w in face f2
		double R1,S2; // sector radii, near side R*, opp side S*
		
		HalfEdge hedge=p.packDCEL.findHalfEdge(edge);
		if (hedge==null || hedge.isBdry())
			return 0;
		int v=edge.v;
		int w=edge.w;
		int v2w=p.nghb(v,w);
		int w2v=p.nghb(w,v);
		
		// find two faces: f_right to right of <v,w>, f_left to left
		f_right=hedge.twin.face.faceIndx;
		f_left=hedge.face.faceIndx;
		j1=p.packDCEL.faces[f_right].getVertIndx(v);
		j2=p.packDCEL.faces[f_left].getVertIndx(w);

		// find opposite faces
		int oppF1, oppF2; // index of faces across opposite sides
		int k=w2v+1;
		if (k<=p.countFaces(w)-1)
			oppF1=p.getFaceFlower(w,k);
		else {
			if (p.isBdry(w))
				oppF1=-1;
			else oppF1=p.getFaceFlower(w,0);
		}
		k=v2w+1;
		if (k<=p.countFaces(v)-1)
			oppF2=p.getFaceFlower(v,k);
		else {
			if (p.isBdry(v))
				oppF2=-1;
			else oppF2=p.getFaceFlower(v,0);
		}
		
		// find near faces
		int nearF1, nearF2; // index of faces across near sides
		if (v2w>1)
			nearF1=p.getFaceFlower(v,v2w-2);
		else {
			if (p.isBdry(v))
				nearF1=-1;
			else {
				int num=p.countFaces(v);
				nearF1=p.getFaceFlower(v,(v2w+num-2)%num);
			}
		}
		if (w2v>1)
			nearF2=p.getFaceFlower(w,w2v-2);
		else {
			if (p.isBdry(w))
				nearF2=-1;
			else {
				int num=p.countFaces(w);
				nearF2=p.getFaceFlower(w,(w2v+num-2)%num);
			}
		}

		// find various indices
		int oppK1=-1; // index of w as oppF1 'vert'
		int oppK2=-1; // index of v as oppF2 'vert'
		int nearK1=-1; // index of v as nearF1 'vert'
		int nearK2=-1; // index of w as nearF2 'vert'
		
		if (oppF1>0)
			oppK1=asp[oppF1].vertIndex(w);
		if (oppF2>0)
			oppK2=asp[oppF2].vertIndex(v);
		if (nearF1>0)
			nearK1=asp[nearF1].vertIndex(v);
		if (nearF2>0)
			nearK2=asp[nearF2].vertIndex(w);
		
		// scale sides to make shared edge length 1
		double delt1=1.0/asp[f_right].sidelengths[(j1+2)%3];
		double delt2=1.0/asp[f_left].sidelengths[(j2+2)%3];
		for (int j=0;j<3;j++){
			asp[f_right].sidelengths[(j1+j)%3] *=delt1;
			asp[f_left].sidelengths[(j2+j)%3] *=delt2;
		}
		
		// scale near triangle sides to agree with near f1, near f2
		if (nearF1>0){
		double gamm1=asp[f_right].sidelengths[j1]/asp[nearF1].sidelengths[(nearK1+2)%3];
		for (int j=0;j<3;j++){
				asp[nearF1].sidelengths[(nearK1+j)%3] *=gamm1;
			}
		}
		if (nearF2>0){
			double gamm2=asp[f_left].sidelengths[j2]/asp[nearF2].sidelengths[(nearK2+2)%3];
			for (int j=0;j<3;j++){
					asp[nearF2].sidelengths[(nearK2+j)%3] *=gamm2;
				}
			}
		
		// scale opp triangle sides to agree with opp f1, opp f2
		if (oppF1>0){
			double beta1=asp[f_right].sidelengths[(j1+1)%3]/asp[oppF1].sidelengths[oppK1];
			for (int j=0;j<3;j++){
				asp[oppF1].sidelengths[(oppK1+j)%3] *=beta1;
				}
			}
		if (oppF2>0){
			double beta2=asp[f_left].sidelengths[(j2+1)%3]/asp[oppF2].sidelengths[oppK2];
			for (int j=0;j<3;j++){
				asp[oppF2].sidelengths[(oppK2+j)%3] *=beta2;
				}
			}
		
		// compute radii: R1/S1 are near/opp of 'v'
		R1=(.5)*(asp[f_right].sidelengths[j1]+asp[f_right].sidelengths[(j1+2)%3]-asp[f_right].sidelengths[(j1+1)%3]);
		S2=(.5)*(asp[f_left].sidelengths[(j2+1)%3]+asp[f_left].sidelengths[(j2+2)%3]-asp[f_left].sidelengths[j2]);
		
		// Case 1: R1<S2 (need R1, R2 to inc)
		if (R1<S2){
			
			// error term
			double E=(.5)*Math.abs(S2-R1);
			
			// quantities involving R1 
			double O1=asp[f_right].sidelengths[(j1+1)%3];
			double N1=asp[f_right].sidelengths[(j1+1)%3];
			double T1=(.5)*(1.0-Math.abs(asp[f_right].sidelengths[j1]-asp[f_right].sidelengths[(j1+1)%3]));
			if (oppF1>0)
				O1=asp[oppF1].sidelengths[oppK1]-Math.abs(asp[oppF1].sidelengths[(oppK1+1)%3]-asp[oppF1].sidelengths[(oppK1+2)%3]);
			if (nearF1>0)
				N1=asp[nearF1].sidelengths[(nearK1+1)%3]+asp[nearF1].sidelengths[nearK1]-asp[nearF1].sidelengths[(nearK1+2)%3];
			
			// quantities involving R2 
			double O2=asp[f_left].sidelengths[(j2+1)%3];
			double N2=asp[f_left].sidelengths[(j2+1)%3];
			double T2=(.5)*(1.0-Math.abs(asp[f_left].sidelengths[j2]-asp[f_left].sidelengths[(j2+1)%3]));
			if (oppF2>0)
				O2=asp[oppF2].sidelengths[oppK2]-Math.abs(asp[oppF2].sidelengths[(oppK2+1)%3]-asp[oppF2].sidelengths[(oppK2+2)%3]);
			if (nearF2>0)
				N2=asp[nearF2].sidelengths[(nearK2+1)%3]+asp[nearF2].sidelengths[nearK2]-asp[nearF2].sidelengths[(nearK2+2)%3];
			
			// set upper bound on adjustments affecting R1
			double M1=Math.min(asp[f_right].sidelengths[(j1+1)%3], Math.min(T1, Math.min(O1, N1)));
			
			// set upper bound on adjustments affecting R2
			double M2=Math.min(asp[f_left].sidelengths[(j2+1)%3], Math.min(T2, Math.min(O2, N2)));
			
			// joint upper bound
			double M3=Math.min(M1, M2);
			
			// now incorporate error term to make sure error decreases
			double M=Math.min(E, M3);
			
			// adjustments (as ratio of an extreme adjustment)
			double eps=(.5)*t*M;
			asp[f_right].sidelengths[(j1+1)%3] -=eps;
			asp[f_right].sidelengths[j1] +=eps;
			asp[f_left].sidelengths[(j2+1)%3] -=eps;
			asp[f_left].sidelengths[j2] +=eps;
			
			if (oppF1>0) 
				asp[oppF1].sidelengths[oppK1]=asp[f_right].sidelengths[(j1+1)%3];
			if (nearF1>0)
				asp[nearF1].sidelengths[(nearK1+2)%3]=asp[f_right].sidelengths[j1];
			
			if (oppF2>0) 
				asp[oppF2].sidelengths[oppK2]=asp[f_left].sidelengths[(j2+1)%3];
			if (nearF2>0)
				asp[nearF2].sidelengths[(nearK2+2)%3]=asp[f_left].sidelengths[j2];
		}
		
		// Case 2: need S1, S2 inc
		if (S2<R1){
			
			// error term
			double E=(.5)*Math.abs(S2-R1);
			
			// quantities involving S1
			double O1=asp[f_right].sidelengths[j1];
			double N1=asp[f_right].sidelengths[j1];
			double T1=(.5)*(1.0-Math.abs(asp[f_right].sidelengths[j1]-asp[f_right].sidelengths[(j1+1)%3]));
			if (nearF1>0)
				O1=asp[nearF1].sidelengths[(nearK1+2)%3]-Math.abs(asp[nearF1].sidelengths[(nearK1+1)%3]-asp[nearF1].sidelengths[nearK1]);
			if (oppF1>0)
				N1=asp[oppF1].sidelengths[(oppK1+1)%3]+asp[oppF1].sidelengths[(oppK1+2)%3]-asp[oppF1].sidelengths[oppK1];
			
			// quantities involving S2
			double O2=asp[f_left].sidelengths[j2];
			double N2=asp[f_left].sidelengths[j2];
			double T2=(.5)*(1.0-Math.abs(asp[f_left].sidelengths[j2]-asp[f_left].sidelengths[(j2+1)%3]));
			if (nearF2>0)
				O2=asp[nearF2].sidelengths[(nearK2+2)%3]-Math.abs(asp[nearF2].sidelengths[(nearK2+1)%3]-asp[nearF2].sidelengths[nearK2]);
			if (oppF2>0)
				N2=asp[oppF2].sidelengths[(oppK2+1)%3]+asp[oppF2].sidelengths[(oppK2+2)%3]-asp[oppF2].sidelengths[oppK2];
			
			// upper bound on adjustments affecting S1
			double M1=Math.min(asp[f_right].sidelengths[j1], Math.min(T1, Math.min(O1, N1)));
			
			// upper bound on adjustments affecting S2
			double M2=Math.min(asp[f_left].sidelengths[j2], Math.min(T2, Math.min(O2, N2)));
			
			// joint upper bound
			double M3=Math.min(M1, M2);
			
			// now incorporate error term to make sure error decreases
			double M=Math.min(E, M3);
			
			//adjustments 
			double eps=(.5)*t*M;
			asp[f_right].sidelengths[j1] -=eps;
			asp[f_right].sidelengths[(j1+1)%3] +=eps;
			asp[f_left].sidelengths[j2] -=eps;
			asp[f_left].sidelengths[(j2+1)%3] +=eps;
			
			if (nearF1>0)
				asp[nearF1].sidelengths[(nearK1+2)%3]=asp[f_right].sidelengths[j1];
			if (oppF1>0) 
				asp[oppF1].sidelengths[oppK1]=asp[f_right].sidelengths[(j1+1)%3];
			
			if (nearF2>0)
				asp[nearF2].sidelengths[(nearK2+2)%3]=asp[f_left].sidelengths[j2];
			if (oppF2>0) 
				asp[oppF2].sidelengths[oppK2]=asp[f_left].sidelengths[(j2+1)%3];
		}
		
		return 1;
	}
	
	//ORIGINAL VERSION
	
	/*public static int edgeAdjust(PackData p,EdgeSimple edge,double t,TriAspect []asp) {
		int f1,f2; // face on right/left (resp) of edge
		int j1; // index of v in face f1
		int j2; // index of w in face f2
		double R1,S1,R2,S2; // sector radii, near side R*, opp side S*
		double lamOpp1=1.0,lamOpp2=1.0; // scale factor for opposite/near sides
		double lamNear1=1.0,lamNear2=1.0;
		
		int v=edge.v;
		int w=edge.w;
		int v2w=p.nghb(v,w);
		int w2v=p.nghb(w,v);
		
		// return if <v,w> is not an interior edge
		if (v2w==0 && p.isBdry(v) ||
				w2v==0 && p.isBdry(w))
			return 0;
				
		// find two faces: f1 to right of <v,w>, f2 to left
		f1=p.left_face(w,v)[0];
		f2=p.left_face(v,w)[0];
		// compute the sector radii: R1/S1 are near/opp of 'v'.
		j1=p.faces[f1].vertIndx(v);
		j2=p.faces[f2].vertIndx(w);
		R1=(.5)*(asp[f1].sides[j1]+asp[f1].sides[(j1+2)%3]-asp[f1].sides[(j1+1)%3]);
		S1=(.5)*(asp[f1].sides[(j1+1)%3]+asp[f1].sides[(j1+2)%3]-asp[f1].sides[j1]);
		R2=(.5)*(asp[f2].sides[j2]+asp[f2].sides[(j2+2)%3]-asp[f2].sides[(j2+1)%3]);
		S2=(.5)*(asp[f2].sides[(j2+1)%3]+asp[f2].sides[(j2+2)%3]-asp[f2].sides[j2]);

		// find opposite faces
		int oppF1, oppF2; // index of faces across opposite sides
		int k=w2v+1;
		if (k<=p.getNum(w)-1)
			oppF1=p.getFaceFlower(w,k);
		else {
			if (p.isBdry(w))
				oppF1=-1;
			else oppF1=p.getFaceFlower(w,0);
		}
		k=v2w+1;
		if (k<=p.getNum(v)-1)
			oppF2=p.getFaceFlower(v,k);
		else {
			if (p.isBdry(v))
				oppF2=-1;
			else oppF2=p.getFaceFlower(v,0);
		}
		
		// find near faces
		int nearF1, nearF2; // index of faces across near sides
		if (v2w>1)
			nearF1=p.getFaceFlwoer(v,v2w-2);
		else {
			if (p.isBdry(v))
				nearF1=-1;
			else {
				int num=p.getNum(v);
				nearF1=p.getFaceFlower(v,(v2w+num-2)%num);
			}
		}
		if (w2v>1)
			nearF2=p.getFaceFlower(w,w2v-2);
		else {
			if (p.isBdry(w))
				nearF2=-1;
			else {
				int num=p.getNum(w);
				nearF2=p.getFaceFlower(w,(w2v+num-2)%num);
			}
		}

		// find various indices
		int oppK1=-1; // index of w as oppF1 'vert'
		int oppK2=-1; // index of v as oppF2 'vert'
		int nearK1=-1; // index of v as nearF1 'vert'
		int nearK2=-1; // index of w as nearF2 'vert'
		
		if (oppF1>0)
			oppK1=asp[oppF1].vertIndex(w);
		if (oppF2>0)
			oppK2=asp[oppF2].vertIndex(v);
		if (nearF1>0)
			nearK1=asp[nearF1].vertIndex(v);
		if (nearF2>0)
			nearK2=asp[nearF2].vertIndex(w);
		
		// multiplication factors
		double mf1=2.0*S1*t/((asp[f1].sides[j1]+asp[f1].sides[(j1+1)%3])*
				((1-t)*R1+(1+t)*S1));
		double mf2=2.0*S2*t/((asp[f2].sides[j2]+asp[f2].sides[(j2+1)%3])*
				((1-t)*R2+(1+t)*S2));
		
		// Now to compute the various scale factors
		if (R1/S1>1 && R2/S2>1) {
			double eps=mf1*(R1-S1);
			lamOpp1=1+eps;
			lamNear1=1-eps;
			eps=mf2*(R2-S2);
			lamOpp2=1+eps;
			lamNear2=1-eps;
		}
		else if (R1/S1<1 && R2/S2<1) {
			double eps=mf1*(S1-R1);
			lamOpp1=1-eps;
			lamNear1=1+eps;
			eps=mf2*(S2-R2);
			lamOpp2=1-eps;
			lamNear2=1+eps;
		}
		else if ((R1*R2)/(S1*S2)>1) {
			if (R1/S1>1) {
				double eps=mf1*(R1-S1);
				lamOpp1=1+eps;
				lamNear1=1-eps;
			}
			else {
				double eps=mf2*(R2-S2);
				lamOpp2=1+eps;
				lamNear2=1-eps;
			}
		}
		else if ((R1*R2)/(S1*S2)<1) {
			if (R1/S1<1) {
				double eps=mf1*(S1-R1);
				lamOpp1=1-eps;
				lamNear1=1+eps;
			}
			else if (R2/S2<1) {
				double eps=mf2*(S2-R2);
				lamOpp2=1-eps;
				lamNear2=1+eps;
			}
		}
		
		// Adjustments to two sides of f1 and of f2
		asp[f1].sides[(j1+1)%3] *= lamOpp1;
		asp[f1].sides[j1] *= lamNear1;
		asp[f2].sides[(j2+1)%3] *= lamOpp2;
		asp[f2].sides[j2] *= lamNear2;
		
		// Adjustments in opposite face 'sides'
		if (oppF1>0) 
			asp[oppF1].sides[oppK1] *=lamOpp1;
		if (oppF2>0)
			asp[oppF2].sides[oppK2] *=lamOpp2;
		
		// Adjustment in near face 'sides'
		if (nearF1>0)
			asp[nearF1].sides[(nearK1+2)%3] *=lamNear1;
		if (nearF2>0)
			asp[nearF2].sides[(nearK2+2)%3] *=lamNear2;
		
		// Here we may have to put some data updates, eg., for asp data.
		return 1;
	}*/
	
	/**
	 * Find 'edge' consistency error using TriAspect 'labels'.
	 * Suppose edge e = (u,v), f_left and f_right are left/right faces,
	 * and labels ru, Ru are near/far labels for f_left, while 
	 * rv, Rv are near/far labels for f_right, then we return
	 * (Ru/ru)*(Rv/rv). For fully consistent edge, this will be 1.0.
	 * 
	 * @param p, PackData 
	 * @param edge
	 * @param asp[], TriAspect
	 * @return 1.0 if not interior edge.
	 */
	public static double edgeRatioError(PackData p,EdgeSimple edge,TriAspect []asp) {
		if (p.isBdry(edge.v) && p.isBdry(edge.w)) // bdry edge 
			return 1.0;
		int []lf=p.left_face(edge.v,edge.w);
		int lface=lf[0];
		lf=p.left_face(edge.w,edge.v);
		int rface=lf[0];
		int lj=asp[lface].vertIndex(edge.v);
		int rj=asp[rface].vertIndex(edge.w);
		double prd=asp[lface].labels[(lj+1)%3];
		prd /=asp[lface].labels[lj];
		prd *=asp[rface].labels[(rj+1)%3];
		prd /=asp[rface].labels[rj];
		return prd;
	}

	/** 
	 * Return weak consistency error for interior 'v'.
	 * This is product of leftlength/rightlength for all 
	 * faces in star(v).
	 * @param p, PackData
	 * @param v vertex
	 * @param asp[], TriAspect
	 * @return 1.0 if v not interior.
	 */  
	public static double weakConError(PackData p,int v,TriAspect []asp) {
		if (p.isBdry(v))
			return 1.0;
		double rtio=1.0;
		int[] faceFlower=p.getFaceFlower(v);
		for (int j=0;j<p.countFaces(v);j++) {
			int ff=faceFlower[j];
			int k=asp[ff].vertIndex(v);
			rtio *= asp[ff].sidelengths[(k+2)%3]; // left sidelength
			rtio /= asp[ff].sidelengths[k]; // right sidelength
		}
		return rtio;
	}

	/**
	 * Return the 'worst' edge consistency error among all edges:
	 * namely, for edge e, find x=edge ratio error on e, then 
	 * compute |log(x)|. Return 'EdgeSimple' e for which this is
	 * largest. 
	 * @return EdgeSimple
	 */
	public EdgeSimple worstEdge() {
		double maxwe=0.0;
		EdgeSimple maxES=null;
		for (int v=1;v<=packData.nodeCount;v++) {
			int[] flower=packData.getFlower(v);
			for (int j=0;j<flower.length;j++) {
				int w=flower[j];
				if ((!packData.isBdry(v) || !packData.isBdry(w)) && w>v) {
					double ere=Math.abs(Math.log(edgeRatioError(packData,new EdgeSimple(v,w),aspects)));
					if (ere>maxwe) {
						maxwe=ere;
						maxES=new EdgeSimple(v,w);
					}
				}
					
			}
		}
		if (maxES==null) {
			Oops("Found no max edge ratio");
			return null;
		}
		return maxES;	
	}
	
	/**
	 * Run trials over a grid and put the results in a buffer.
	 * Output lines: A B t T c a.x a.y b.x b.y z's
	 *   inputs A,B, modulus t, Teichmuller T, affine coefficient c,
	 *   complex a b, corner points, complex z[0], z[1], z[2], z[3]
	 * 
	 * @param N
	 * @param grid doubles lx ly ux uy
	 * @return 
	 */
	public StringBuilder runGridData(int N,double []grid) {
		
		StringBuilder output=new StringBuilder("%%Affine packing data from CirclePack run: \n" +
				"%% nodeCount="+packData.nodeCount+";"+
				"grid from ("+grid[0]+","+grid[1]+") to ("+grid[2]+","+grid[3]+"), (N+1)x(N+1) with N="+
				N+"; A=e^x, B=e^y.\n");
		output.append("%%  matlab vector of data:\n");
		output.append("%%    rundata = [i j A B tau Teich affcoeff alpha beta]\n");
				// may want these later "z[0] z[1] z[2] z[3] ]\n");
		output.append("\nRunData = [\n");
		
		// check that 'aspects' is ready
		if (aspects==null || aspects.length!=(packData.faceCount+1))
			resetAspects();
		
		boolean okay=true;
		for (int i=0;(i<=N && okay);i++) {
			for (int j=0;(j<=N && okay);j++) {
				
				// Note: parameters are logarithms, since always positive
				double A=Math.exp(grid[0]+(grid[2]-grid[0])*i/N);
				double B=Math.exp(grid[1]+(grid[3]-grid[1])*j/N);
				
				// set affine data
				boolean result = ProjStruct.affineSet(packData,aspects,A, B);
				if (!result) {
					msg("affine has failed for A, B = "+A+","+B);
					okay=false;
					break; 
				}
				
				// do affpack
				NodeLink vlink=new NodeLink(packData,"a");
				int count = ProjStruct.vertRiffle(packData, aspects,1,PASSES,vlink);
				if (count < 0) {
					msg("affpack seems to have failed");
					okay=false;
					break;
				}
				
				// gather torus data
				TorusData torusData=ProjStruct.getTorusData(packData,true);
				if (torusData==null) {
					msg("failed to get torus data");
					okay=false;
					break;
				}
				
				// on last run, center the data
				if (i==N && j==N) {
					RedEdge rtrace=packData.packDCEL.redChain;
					double rad=rtrace.getRadius();
					Complex cent=rtrace.getCenter();
					int safety=packData.faceCount+1;
					double minx=cent.x-rad;
					double miny=cent.y-rad;
					double maxx=cent.x+rad;
					double maxy=cent.y+rad;
					rtrace=rtrace.nextRed;
					while (rtrace!=packData.packDCEL.redChain && safety>0) {
						rad=rtrace.getRadius();
						cent=rtrace.getCenter();
						minx=((cent.x-rad)<minx) ? (cent.x-rad) : minx;
						miny=((cent.y-rad)<miny) ? (cent.y-rad) : miny;
						maxx=((cent.x+rad)>maxx) ? (cent.x+rad) : maxx;
						maxy=((cent.y+rad)>maxy) ? (cent.y+rad) : maxy;
						safety--;
					} 
					if (safety>0) {
						cpCommand("set_screen -b "+minx+" "+miny+" "+maxx+" "+maxy);
					}
				}

				// store desired data
				// Output lines: A B t T c a a b b z's
				//  inputs A,B, modulus t, Teichmuller T, affine coefficient c,
				//   complex a b, corner points, complex z[0], z[1], z[2], z[3]
				output.append(i+" "+j+"  "+A+" "+B+"  "+
						torusData.tau.toString()+"    "+
						torusData.teich.toString()+"   "+
						torusData.affCoeff.toString()+"   ");
//				for (int k=0;k<4;k++)
//						output.append(" "+torusData.cornerPts[k].toString());
				output.append(";\n\n");
				
				// debug message
				if (okay) {
					msg("run okay for A, B = "+A+","+B);
				}
			} // end of j loop
		} // end of i loop
		if (okay)
			msg("RUN seems to have workded");
		else
			msg("RUN encountered some error");
		output.append("\n];\n%% end of data\n");
		
		return output;
	}
	
	/**
	 * This is where the user's commands are "parsed"
	 */
	public int cmdParser(String cmd, Vector<Vector<String>> flagSegs) {
		Vector<String> items = null;
		
		// ======= colorV =================
		if (cmd.startsWith("colorV")) {
			String mode="weak"; // default
			try {
				mode=flagSegs.get(0).get(0);
			} catch (Exception ex){}
			Vector<Double> data=new Vector<Double>(packData.nodeCount);
			for (int v=1;v<=packData.nodeCount;v++) 
				data.add(v,ProjStruct.weakConError(packData,aspects,v));
			Vector<Color> colIndices=ColorUtil.blue_red_diff_ramp_Color(data);

			for (int v=1;v<=packData.nodeCount;v++) {
				Color coLor=colIndices.get(v);
				packData.setCircleColor(v,new Color(
						coLor.getRed(),coLor.getGreen(),coLor.getBlue()));
			}
			return 1;
		}
		
		// ======= riffle: test routines (various ways) ==============
		else if (cmd.startsWith("riffle")) {
			
			int mode=1; // default
			NodeLink vlink=null;
			try {
				// first int is mode, else default
				items=flagSegs.get(0);
				try {
					mode=Integer.valueOf(items.get(0));
					flagSegs.remove(0);
				} catch (Exception ex) {}
				
				// may be one more flag sequence, -v {v..}
				if (flagSegs.size()>0) {
					items=flagSegs.remove(0);
					if (items.get(0).equals("-v")) {
						items.remove(0);
						vlink=new NodeLink(packData,items);
						
					}
					else
						throw new ParserException("riffle {m} -v {v..}");
				}
			} catch (Exception ex) {
				throw new ParserException("usage: riffle {m}: 1=ang sum, 2=weak, 3=effective, 4=side");
			}
			if (mode==1 || mode == 2) { // ang sum or weak
				int count = ProjStruct.vertRiffle(packData, aspects,mode,PASSES,vlink);
				if (count < 0) {
					Oops("riffle for aims seems to have failed");
					return 0;
				}
				return count;
			}
			else if (mode==3) { // effective radii (needs to be weakly consistent,
				// but won't preserve consistency)
			}
			else if (mode==4) { // sides
			// TODO: 	
			// reset 'labels' vector from 'sides'
			for (int f=1;f<=packData.faceCount;f++) 
				aspects[f].sides2Labels();
			}
		}
		
		// =========== rand ========
		else if (cmd.startsWith("rand")) {
			int count=0;
			double rmin=.5;
			double rmax=2.0;
			FaceLink flink=null;
			try {
				Iterator<Vector<String>> fs=flagSegs.iterator();
				while (fs.hasNext()) {
					items=fs.next();
					if (items.get(0).equals("-r")) {
						rmin=Double.parseDouble(items.get(1));
						rmax=Double.parseDouble(items.get(2));
					}
					else if (items.get(0).equals("-f")) {
						items.remove(0);
						flink=new FaceLink(packData,items);
					}
				}
			} catch (Exception ex) {}
			
			if (flink==null)
				flink=new FaceLink(packData,"a");
			
			Iterator<Integer> fl=flink.iterator();
			while (fl.hasNext()) {
				double diff=rmax-rmin;
				int f=fl.next();
				double []newlabels=new double[3];
				newlabels[0]=rand.nextDouble()*diff+rmin;
				newlabels[1]=rand.nextDouble()*diff+rmin;
				newlabels[2]=rand.nextDouble()*diff+rmin;
				aspects[f].labels=newlabels;
				aspects[f].labels2Sides();
				count++;
			}
			return count;
		}
			
		// ========= Run a grid of experiments to collect data
		else if (cmd.startsWith("RUN")) {
			
			// get filename, remove from flagSegs
			if (!StringUtil.ckTrailingFileName(flagSegs))
				Oops("missing the file name");
			StringBuilder strbuf=new StringBuilder("");
			int code=CPFileManager.trailingFile(flagSegs, strbuf);
			File file=new File(strbuf.toString());
			boolean append=false;
			if ((code & 02) == 02) // append
				append=true;
			BufferedWriter fp=CPFileManager.openWriteFP((File)CPFileManager.PackingDirectory,append,
					file.getName(),false);
			
			// get other info
			int N=100;
			double []grid=new double[4];
			try {
				while (flagSegs.size()>0) {
					items=flagSegs.remove(0);
					String str=items.remove(0);
					if (StringUtil.isFlag(str)) {
						char c=str.charAt(1);
						switch(c) {
						case 'N': // number of grid points
						{
							N=Integer.parseInt(items.get(0));
							break;
						}
						case 'b': // box: lx, ly, ux, uy
						{
							grid[0]=Double.parseDouble(items.get(0));
							grid[1]=Double.parseDouble(items.get(1));
							grid[2]=Double.parseDouble(items.get(2));
							grid[3]=Double.parseDouble(items.get(3));
							break;
						}
						} // end of switch
					} // end of flags
				} // end of while
			} catch (Exception ex) {
				throw new ParserException("problem with 'RUN': "+ex.getMessage());
			}
			
			// affine, affpack, afflayout, tD, write data
			// run the trials
			StringBuilder strBuild=runGridData(N,grid);
			
			try {
				fp.append(strBuild.toString());
				fp.flush();
				fp.close();
				CirclePack.cpb.msg("Wrote '"+file.getName()+"' to "+
						CPFileManager.PackingDirectory);
			} catch (Exception iox) {
				Oops("Something went wrong with saving 'RUN' results");
				return 0;
			}
			
			return 1;
		}
		// ======== matdat ===============
		if (cmd.startsWith("matd")) {
			double Astart=1.0;
			double Adelta=.1;
			int An=1;
			double Bstart=1.0;
			double Bdelta=.1;
			int Bn=1;
			String datafile=null;
			try {
				items=flagSegs.get(0);
				Astart=Double.parseDouble(items.get(0));
				Adelta=Double.parseDouble(items.get(1));
				An=Integer.parseInt(items.get(2));
				Bstart=Double.parseDouble(items.get(3));
				Bdelta=Double.parseDouble(items.get(4));
				Bn=Integer.parseInt(items.get(5));
				items=flagSegs.get(1);
				datafile=items.get(items.size()-1);
			} catch (Exception ex) {
				Oops("failed to read 'matdat' data correctly");
			}
			
			double []Adata=new double[An+2];
			double []Bdata=new double[Bn+2];
			for (int j=1;j<=(An+1);j++)
				Adata[j]=Astart+(j-1)*Adelta;
			for (int j=1;j<=(Bn+1);j++)
				Bdata[j]=Bstart+(j-1)*Bdelta;
			Complex [][]tau=new Complex[An+2][Bn+2];
			Complex [][]teich=new Complex[An+2][Bn+2];
			Complex [][]alpha=new Complex[An+2][Bn+2];
			Complex [][]beta=new Complex[An+2][Bn+2];
			Complex [][]xrat=new Complex[An+2][Bn+2];
			Complex [][]affC=new Complex[An+2][Bn+2];
			Complex [][]z1=new Complex[An+2][Bn+2];
			Complex [][]z2=new Complex[An+2][Bn+2];
			Complex [][]z3=new Complex[An+2][Bn+2];
			Complex [][]z4=new Complex[An+2][Bn+2];
			NodeLink vlink=new NodeLink(packData,"a");
			
			for (int a=1;a<=An+1;a++) {
				for (int b=1;b<=Bn+1;b++) {
					double A=Adata[a];
					double B=Bdata[b];
					
					// set affine parameters
					boolean result = ProjStruct.affineSet(packData,aspects,A, B);
					if (!result)
						Oops("affine has failed in 'matdat', A="+A+", B="+B);
					
					// repack
					int count = ProjStruct.vertRiffle(packData, aspects,1,PASSES,vlink);
					if (count < 0)
						Oops("affpack seems to have failed in 'matdat'");
						
					// layout
					// TODO: OBE
//					ProjStruct.treeLayout(packData,dTree,aspects);
					
					// compute data
					TorusData tD=ProjStruct.getTorusData(packData,false);
					if (tD==null) 
						Oops("failed to compute 'TorusData' in 'matdat'");
					
					// store data in matrices
					tau[a][b]=new Complex(tD.tau);
					teich[a][b]=new Complex(tD.teich);
					xrat[a][b]=new Complex(tD.x_ratio);
					affC[a][b]=new Complex(tD.affCoeff);
					z1[a][b]=new Complex(tD.cornerPts[0]);
					z2[a][b]=new Complex(tD.cornerPts[1]);
					z3[a][b]=new Complex(tD.cornerPts[2]);
					z4[a][b]=new Complex(tD.cornerPts[3]);

				} // end of for on b
			} // end of for on a
					
			// open file
			BufferedWriter bw=CPFileManager.openWriteFP(datafile,false);
			
			try {
				
				// header
				bw.write("%% matdat run output for matlab: nodecount="+
						packData.nodeCount+"\n");
				
				// A values
				bw.write(" A=[\n");
				for (int a=1;a<=(An+1);a++) {
					bw.write(Double.toString(Adata[a])+"\n");
				}
				bw.write("];\n\n");
				
				// B values
				bw.write(" B=[\n");
				for (int b=1;b<=(Bn+1);b++) {
					bw.write(Double.toString(Bdata[b])+"\n");
				}
				bw.write("];\n\n");
				
				// tau
				bw.write("tau =[\n");
				for (int a=1;a<=(An+1);a++) {
					for (int b=1;b<=(Bn+1);b++) {
						bw.write(Double.toString(tau[a][b].x)+" + "+Double.toString(tau[a][b].y)+"i  ");
					}
				}
				bw.write("\n");
				bw.write("];\n");
				
				// teich
				bw.write("teich =[\n");
				for (int a=1;a<=(An+1);a++) {
					for (int b=1;b<=(Bn+1);b++) {
						bw.write(Double.toString(teich[a][b].x)+" + "+Double.toString(teich[a][b].y)+"i  ");
					}
				}
				bw.write("\n");
				bw.write("];\n");
								
				// alpha
				bw.write("alpha =[\n");
				for (int a=1;a<=(An+1);a++) {
					for (int b=1;b<=(Bn+1);b++) {
						bw.write(Double.toString(alpha[a][b].x)+" + "+Double.toString(alpha[a][b].y)+"i  ");
					}
				}
				bw.write("\n");
				bw.write("];\n");
								
				
				// beta
				bw.write("beta =[\n");
				for (int a=1;a<=(An+1);a++) {
					for (int b=1;b<=(Bn+1);b++) {
						bw.write(Double.toString(beta[a][b].x)+" + "+Double.toString(beta[a][b].y)+"i  ");
					}
				}
				bw.write("\n");
				bw.write("];\n");
								
				
				// affC
				bw.write("affCoeff =[\n");
				for (int a=1;a<=(An+1);a++) {
					for (int b=1;b<=(Bn+1);b++) {
						bw.write(Double.toString(affC[a][b].x)+" + "+Double.toString(affC[a][b].y)+"i  ");
					}
				}
				bw.write("\n");
				bw.write("];\n");
								
			
				// z1
				bw.write("z1 =[\n");
				for (int a=1;a<=(An+1);a++) {
					for (int b=1;b<=(Bn+1);b++) {
						bw.write(Double.toString(z1[a][b].x)+" + "+Double.toString(z1[a][b].y)+"i  ");
					}
				}
				bw.write("\n");
				bw.write("];\n");
								
				
				// z2
				bw.write("z2 =[\n");
				for (int a=1;a<=(An+1);a++) {
					for (int b=1;b<=(Bn+1);b++) {
						bw.write(Double.toString(z2[a][b].x)+" + "+Double.toString(z2[a][b].y)+"i  ");
					}
				}
				bw.write("\n");
				bw.write("];\n");
								
				
				// z3 
				bw.write("z3 =[\n");
				for (int a=1;a<=(An+1);a++) {
					for (int b=1;b<=(Bn+1);b++) {
						bw.write(Double.toString(z3[a][b].x)+" + "+Double.toString(z3[a][b].y)+"i  ");
					}
				}
				bw.write("\n");
				bw.write("];\n");
								
				
				// z4
				bw.write("z4 =[\n");
				for (int a=1;a<=(An+1);a++) {
					for (int b=1;b<=(Bn+1);b++) {
						bw.write(Double.toString(z4[a][b].x)+" + "+Double.toString(z4[a][b].y)+"i  ");
					}
				}
				bw.write("\n");
				bw.write("];\n");
								
				
				// x_ratio
				bw.write("xratio =[\n");
				for (int a=1;a<=(An+1);a++) {
					for (int b=1;b<=(Bn+1);b++) {
						bw.write(Double.toString(xrat[a][b].x)+" + "+Double.toString(xrat[a][b].y)+"i  ");
					}
				}
				bw.write("\n");
				bw.write("];\n");
							
				// footer
				bw.write("%% end of matdat\n");

				// finish files
				bw.flush();
				bw.close();
				msg("Wrote 'matdat' data to "+
						CPFileManager.CurrentDirectory+File.separator+datafile);
			} catch (Exception ex) {
				Oops("Problem writing data in 'matdat'");
			}
			
			return 1;
		}
		
		// ========= affpack =========
		if (cmd.startsWith("affpac")) {
			NodeLink vlink=null;
			
			// are vertices specified?
			try {
				items=flagSegs.get(0);
				vlink=new NodeLink(packData,items);
			} catch (Exception ex) {
				vlink=new NodeLink(packData,"a");
			}
			
			int count = ProjStruct.vertRiffle(packData, aspects,1,PASSES,vlink);
			if (count < 0) {
				Oops("affpack seems to have failed");
				return 0;
			}
			return count;
		}
		
		// ======== affine ===========
		else if (cmd.startsWith("affine")) {
			// this routine is tailored for tori: specify side-pair
			// scaling in an attempt to build general affine tori

			if (packData.genus != 1 || packData.getBdryCompCount()>0) {
				int count=0;
				msg("Simply connected case: 'affine' defaults to all 'labels' 1");
				for (int f=1;f<=packData.faceCount;f++) {
					for (int j=0;j<3;j++) 
						aspects[f].labels[j]=1.0;
					count++;
				}
				return count;
			}

			if (aspects==null || aspects.length!=(packData.faceCount+1))
				resetAspects();
			
			// get the user-specified
			double A = 1.2; // default
			double B = .75;
			try {
				items = flagSegs.get(0);
				A = Double.parseDouble((String) items.get(0));
				B = Double.parseDouble((String) items.get(1));
			} catch (Exception ex) {
			}

			boolean result = ProjStruct.affineSet(packData,aspects,A, B);
			if (!result)
				Oops("affine has failed");
			msg("Affine data set: A = " + A + " B = " + B);
			return 1;
		}
		
		// ========= layout/display TriAspect face chain
		if (cmd.startsWith("facech")) {
			items=flagSegs.get(0);
			
			String sub_cmd = (String) items.remove(0);
			char c = sub_cmd.charAt(1); // grab first letter of flag
			sub_cmd = sub_cmd.substring(2); // remove '-' and first char
			
			FaceLink flist=null;
			LinkedList<TriAspect> aspList=null;
			try {
				flist = new FaceLink(packData,items);
				aspList=ProjStruct.layout_facelist(packData,aspects,flist);
				if (aspList==null) 
					Oops("didn't get TriAspect linked list");
			} catch(Exception ex) {
				Oops("facechain usage: facechain -[cf] <f..>");
			}
			
			// set up the display options
			DispFlags circFlags=null;
			DispFlags faceFlags=null;
			if (c=='c' || c=='b') 
				circFlags=new DispFlags(sub_cmd);
			if (c=='f' || c=='b')
				faceFlags=new DispFlags(sub_cmd);
			if (circFlags==null && faceFlags==null)
				return 0; // options 'c', 'f', or 'b' for both

			int count=ProjStruct.dispFaceChain(packData,aspList,true,faceFlags,circFlags);
			
			repaintMe();
			return count;
		}
		
		// ======== log (in temp log file) ============
		else if (cmd.startsWith("log_rad")) {
			File logfile=new File(System.getProperty("java.io.tmpdir"),
				new String("labels_"+ CPBase.debugID + "_log"));
			BufferedWriter dbw = CPFileManager.openWriteFP(logfile,false,false);
			try {
				dbw.write("labels:\n\n");
				for (int f = 1; f <= packData.faceCount; f++) {
					int[] verts=packData.packDCEL.faces[f].getVerts();
					dbw.write("face " + f + ": <" + verts[0] + ","
							+ verts[1] + "," + verts[2] + ">   "
							+ "labels: <" + (double) aspects[f].labels[0] + ","
							+ aspects[f].labels[1] + "," + aspects[f].labels[2]
							+ ">\n");
				}
				dbw.flush();
				dbw.close();
				this.msg("Wrote labels_log to "+logfile.getCanonicalPath());
			} catch (Exception ex) {
				throw new InOutException("labels_log output error");
			}
			return 1;
		}
		
		//========= we ============
		else if (cmd.startsWith("we")) {
			
			EdgeSimple maxES=worstEdge();
			double ere=Math.abs(Math.log(edgeRatioError(packData,maxES,aspects)));
			msg("Max edge ratio is "+String.format("%.6e",ere)+" at edge <"+maxES.v+" "+maxES.w+">");
			return 1;
		}
		
		// ======== Lface ==========
		if (cmd.startsWith("Lface")) {
			DispFlags dflags=new DispFlags("");
			for (int f=1;f<=packData.faceCount;f++) {
				packData.cpDrawing.drawFace(aspects[f].getCenter(0),
						aspects[f].getCenter(1),aspects[f].getCenter(2),null,null,null,dflags);
			}
			repaintMe();
			return 1;
		}
		
		// ======== Ltree ==========
		else if (cmd.startsWith("Ltree")) {
			Iterator<HalfEdge> ft=packData.packDCEL.layoutOrder.iterator(); 
			while (ft.hasNext()) {
				HalfEdge he=ft.next();
				int f=he.face.faceIndx;
				int g=he.twin.face.faceIndx;
				if (f>=0 && g>=0) {
					CircleSimple sc;
					sc=EuclMath.eucl_tri_incircle(aspects[f].getCenter(0),
							aspects[f].getCenter(1),aspects[f].getCenter(2));
					Complex vc=sc.center;
					sc=EuclMath.eucl_tri_incircle(aspects[g].getCenter(0),
							aspects[g].getCenter(1),aspects[g].getCenter(2));
					Complex wc=sc.center;
					DispFlags df=new DispFlags(null);
					df.setColor(Color.green);
					packData.cpDrawing.drawEdge(vc,wc,df);
				}
			}
			repaintMe();
			return 1;
		}
		
		// ========= set_labels =======
		else if (cmd.startsWith("set_lab")) {
			// no flags? default to '-r', based on radii
			if (flagSegs==null || flagSegs.size()==0) {
				flagSegs=StringUtil.flagSeg("-r"); 
			}
			FaceLink facelist;
			int count=0;
			try {
				Iterator<Vector<String>> fls = flagSegs.iterator();
				while (fls.hasNext()) {
					items = fls.next();
					// get option
					String str = items.remove(0);
					if (!StringUtil.isFlag(str))
						return -1;
					char c=str.charAt(1);
					// get facelist iterator
					if (items==null || items.size()==0) // do all
						facelist = new FaceLink(packData, "a");
					else facelist=new FaceLink(packData,items);
					Iterator<Integer> flt=facelist.iterator();
					
					switch(c) {
					case 'r':  { // use current radii
						while (flt.hasNext()) {
							combinatorics.komplex.DcelFace face=packData.packDCEL.faces[flt.next()];
							int[] verts=face.getVerts();
							for (int j = 0; j < 3; j++)
								aspects[face.faceIndx].labels[j]=
									packData.getRadius(verts[j]);
							count++;
						}
						break;
					}
					case 's': { // random
						while (flt.hasNext()) {
							int f=flt.next();
							aspects[f].randomRatio();
							count++;
						}
						break;
					}
					case 'z': { // use stored centers 
						while (flt.hasNext()) {
							int f=flt.next();
							aspects[f].centers2Labels();
							count++;
						}
						break;
					}
					} // end of switch
				} // end of while
			} catch (Exception ex) {
				Oops("Error setting 'labels': "+ex.getMessage());
			}
			return count;
		}

		// ========== set_screen ======
		else if (cmd.startsWith("set_scre")) {
			double mnX=100000.0;
			double mxX=-100000.0;
			double mnY=100000.0;
			double mxY=-100000.0;
			double pr;
			for (int f = 1; f <= packData.faceCount; f++)
				for (int j = 0; j < 3; j++) {
					mnX = ((pr=aspects[f].getCenter(j).x-aspects[f].labels[j])<mnX) ? pr : mnX; 
					mxX = ((pr=aspects[f].getCenter(j).x+aspects[f].labels[j])>mxX) ? pr : mxX; 
					mnY = ((pr=aspects[f].getCenter(j).y-aspects[f].labels[j])<mnY) ? pr : mnY; 
					mxY = ((pr=aspects[f].getCenter(j).y+aspects[f].labels[j])>mxY) ? pr : mxY; 
				}
			cpCommand("set_screen -b "+mnX+" "+mnY+" "+mxX+" "+mxY);
			packData.cpDrawing.repaint();
			return 1;
		}
		
		// ========= sideRif =============
		
		else if (cmd.startsWith("sideRif")) {
			NodeLink vlink=null;
			
			// are vertices specified?
			try {
				items=flagSegs.get(0);
				vlink=new NodeLink(packData,items);
			} catch (Exception ex) {
				vlink=new NodeLink(packData,"a");
			}
			
			// riffle side lengths to get target angle sums
			int its=ProjStruct.sideRiffle(packData,aspects,2000,vlink);
			msg("'sideRif' iterations: "+its);
			
			
			if (its==0) return -1;
			if (its>0) return its;
			return 0;
		}
		
		// =========== avBd ===============
		
//		else if (cmd.startsWith("avBd")){
//			double sum=0;
//			int count=0;
//			double av=0;
//			for (int f=1;f<=packData.faceCount;f++){
//				for (int j=0;j<3;j++){
//					if (aspects[f].redFlags[j]==true && aspects[f].redFlags[(j+1)%3]==true){
//						sum +=aspects[f].sides[j];
//						count++;
//					}
//				}
//			}
//			av=sum/count;
//			for (int f=1;f<=packData.faceCount;f++){
//				for (int j=0;j<3;j++){
//					if (aspects[f].redFlags[j]==true && aspects[f].redFlags[(j+1)%3]==true)
//						aspects[f].sides[j]=av;
//				}
//			}
//			for (int f=1;f<=packData.faceCount;f++){
//				aspects[f].sides2Labels();
//			}
//			msg("Boundary Edge Count:"+count);
//			return 1;
//		}
			
		// ========== rand_ad ============
		else if (cmd.startsWith("rand_ad")) {
			
			rectAd1(packData, aspects);
			
			// reset 'labels' vector from 'sides'
			for (int f=1;f<=packData.faceCount;f++){ 
				aspects[f].sides2Labels();
				}
			
			return 1;
		}
		
		// ========== sc ==================
		else if (cmd.startsWith("sc")) {
			int defaultits=10;
			// are vertices specified?
			try {
				items=flagSegs.get(0);
				defaultits=Integer.parseInt(items.get(0));
			} catch (Exception ex) {}
			
			int[] its=rectAdjust(packData, aspects, defaultits);
			msg("'sc' iterations: "+its[0]);
			msg("edge iterations: "+its[2]);
			msg("'sc' adjustments: "+its[1]);
			
			// reset 'labels' vector from 'sides'
			for (int f=1;f<=packData.faceCount;f++){ 
				aspects[f].sides2Labels();
				}
			
			return 1;
		}
		
		// ========== manip ==================
		else if (cmd.startsWith("manip")) {
			Iterator<Vector<String>> flgs=flagSegs.iterator();
			while (flgs.hasNext()) {
				items=flgs.next();
				String str=items.get(0);
				if (StringUtil.isFlag(str)) {
					char c=str.charAt(1);
					switch (c) {
					case 'e':
					{
						items.remove(0);
						double factor=0.0;
						try{
							factor=Double.parseDouble(items.get(0));
							items.remove(0);
						} catch (Exception ex) {}
						EdgeLink elist=new EdgeLink(packData,items);
						if (elist==null || elist.size()==0) return 0;
						edgeAdjust(packData,elist.get(0),factor,aspects);
						break;
					}
					} // end of switch
				}
				
			}
			return 1;
		}
		
		
		// ========== MoveCent ==================
		else if (cmd.startsWith("MoveCent")) {
			int v=0;
			int w=0;
			// are vertices specified?
			try {
				items=flagSegs.get(0);
				v=Integer.parseInt(items.get(0));
			} catch (Exception ex) {}
			
			packData.setCenter(v,NewCenter(packData,aspects,v));
			int[] faceFlower=packData.getFaceFlower(v);
			for (int j=0;j<packData.countFaces(v);j++){
				int f=faceFlower[j];
				w=aspects[f].vertIndex(v);
				aspects[f].setCenter(NewCenter(packData,aspects,v),w);
				aspects[f].centers2Labels();
			}
			
			return 1;
		}
		
		// ========== randMC ==================
		else if (cmd.startsWith("randMC")) {
			int v=0;
			int w=0;
			int N=0;
			
			while (N<1){
				int n = packData.nodeCount;
				int r = (int)(Math.floor(1+n*rand.nextDouble()));
				if (!packData.isBdry(r)) {
					N=1;
					v=r;
				}
			}
			
			packData.setCenter(v,NewCenter(packData,aspects,v));
			int[] faceFlower=packData.getFaceFlower(v);
			for (int j=0;j<packData.countFaces(v);j++){
				int f=faceFlower[j];
				w=aspects[f].vertIndex(v);
				aspects[f].setCenter(NewCenter(packData,aspects,v),w);
				aspects[f].centers2Labels();
			}
			
			return 1;
		}
		
		// ========== incMC ==================
		else if (cmd.startsWith("incMC")) {
			
			int its=IncNC(packData, aspects);
			msg(its+" iterations");
			
			return 1;
			
		}
		
		// ========== edge ==================
		else if (cmd.startsWith("edge")) {
			
			double [] info12=Edge12(packData,aspects);
			msg("combinatorial length 1-2 edge: "+info12[0]);
			msg("effective length 1-2 edge: "+info12[1]);
			msg("edge count 1-2 edge: "+info12[2]);
			msg("length 1-2 edge: "+info12[3]);
			
			double [] info34=Edge34(packData,aspects);
			msg("combinatorial length 3-4 edge: "+info34[0]);
			msg("effective length 3-4 edge: "+info34[1]);
			msg("edge count 3-4 edge: "+info34[2]);
			msg("length 3-4 edge "+info34[3]);
			
			double [] info23=Edge23(packData,aspects);
			msg("combinatorial length 2-3 edge: "+info23[0]);
			msg("effective length 2-3 edge: "+info23[1]);
			msg("edge count 2-3 edge: "+info23[2]);
			msg("length 2-3 edge "+info23[3]);
			
			double [] info41=Edge41(packData,aspects);
			msg("combinatorial length 4-1 edge: "+info41[0]);
			msg("effective length 4-1 edge: "+info41[1]);
			msg("edge count 4-1 edge: "+info41[2]);
			msg("length 4-1 edge "+info41[3]);

			
			return 1;
			
		}
		
		// ========== AdjBd ==================
		else if (cmd.startsWith("AdjBd")){
			
			AdjBd(packData,aspects);
			
			return 1;
			
		}
		
		// ========== adjBd ==================
		else if (cmd.startsWith("adjBd")){
			
			adjBd(packData,aspects);
			
			return 1;
			
		}

		return super.cmdParser(cmd, flagSegs);
	}
	
	/** 
	 * Override method for cataloging command structures
	 */
	public void initCmdStruct() {
		super.initCmdStruct();
		cmdStruct.add(new CmdStruct("tD",null,null,"Fill 'TorusData' info; must be torus with 2-sidepair form; "));
		cmdStruct.add(new CmdStruct("affine","{a b}",null,"set face ratio data for torus, side pairing factors a, b"));
		cmdStruct.add(new CmdStruct("affpack","{v..}",null,"run iterative affine packing method"));
		cmdStruct.add(new CmdStruct("afflayout",null,null,"layout a fundamental domain using computed ratios"));
		cmdStruct.add(new CmdStruct("disp","-[cfe]flags x...",null,"faces, circles or edges, normal flags, object specs"));
		cmdStruct.add(new CmdStruct("set_screen",null,null,"set screen to get the full fundamental domain"));
		cmdStruct.add(new CmdStruct("riffle","-m {x} [-v {v..}]",null,"Riffle for x: 1=ang sums, 2=weak, "+
				"3=effective, 4=sides"));
		cmdStruct.add(new CmdStruct("Lface",null,null,"draw faces using TriAspect centers, spanning tree"));
		cmdStruct.add(new CmdStruct("Ltree",null,null,"draw dual spanning tree using TriAspect centers"));
		cmdStruct.add(new CmdStruct("set_labels","-[rzst] f..",null,"face 'label' data using: -r = radii, -z = centers, -s= random"));
		cmdStruct.add(new CmdStruct("log_radii",null,null,"write /tmp file with labels"));
		cmdStruct.add(new CmdStruct("status","-[csw] v",null,"No flags? error norms: curvatures, strong/weak consistency\n"+
				"With flags: return single vert info"));
		cmdStruct.add(new CmdStruct("set_eff",null,null,"Using centers, set packing rad to the 'effective' radii"));
		cmdStruct.add(new CmdStruct("ccode","-[cfe] -m m j..",null,"Color code faces, vertices, or edges, mode m"));
		cmdStruct.add(new CmdStruct("equiSides",null,null,"set 'sides' to 1; faces are equilateral"));
		cmdStruct.add(new CmdStruct("manip","-[e] f e..",null,"Various manipulations: will add flags."));
		cmdStruct.add(new CmdStruct("update","-[sl] f..",null,"Update: -s centers to sides; -l sides to labels"));
		cmdStruct.add(new CmdStruct("we",null,null,"returns worst edge wrt strong consistency"));
		cmdStruct.add(new CmdStruct("equiBd",null,null,"sets boundary 'sides' to 1"));
		cmdStruct.add(new CmdStruct("avBd",null,null,"sets boundary 'sides' to average boundary sidelength"));
		cmdStruct.add(new CmdStruct("sc",null,null,"adjusts edges in sc direction"));
		cmdStruct.add(new CmdStruct("err",null,null,"print angle, weak, strong"));
		cmdStruct.add(new CmdStruct("rand_ad",null,null,"chooses random edge to adjust"));
		cmdStruct.add(new CmdStruct("MoveCent","v",null,"moves vertex of face flower"));
		cmdStruct.add(new CmdStruct("randMblueC",null,null,"adjusts random vertex position"));
		cmdStruct.add(new CmdStruct("incMC",null,null,"adjust random centers until 5% decrease in error"));
		cmdStruct.add(new CmdStruct("edge",null,null,"edge 1 --> 2"));
		cmdStruct.add(new CmdStruct("RUN","-b lx ly ux uy -N {n} -[afs] {filename}",null,
				"run experiments on NxN grid of data points, results to filename"));
		cmdStruct.add(new CmdStruct("rand","[-r {x,y}] [-f {f..}]",null,"Set labels randomly for given faces, values in [x,y], [.5,2] be default"));
		cmdStruct.add(new CmdStruct("colorV","{str}",null,"color ramp vertices by mode: 'weak'; 'strong'"));
	}
	
}

