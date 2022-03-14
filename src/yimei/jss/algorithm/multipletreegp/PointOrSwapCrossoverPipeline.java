package yimei.jss.algorithm.multipletreegp;

import ec.EvolutionState;
import ec.Individual;
import ec.gp.GPIndividual;
import ec.gp.GPInitializer;
import ec.gp.GPNode;
import ec.gp.GPTree;
import ec.gp.koza.CrossoverPipeline;

/**
 * Created by fzhang on 9.6.2018.
 */
//randomly choose one type of tree, the trees have 50% chance to do point crossover and 50% chance to swap
public class PointOrSwapCrossoverPipeline extends CrossoverPipeline {
    //
    @Override
    public int produce(final int min,
                       final int max,
                       final int start,
                       final int subpopulation,
                       final Individual[] inds,
                       final EvolutionState state,
                       final int thread)

    {
        // how many individuals should we make?
        int n = typicalIndsProduced();
        if (n < min) n = min;
        if (n > max) n = max;

        // should we bother?
        if (!state.random[thread].nextBoolean(likelihood))
            return reproduce(n, start, subpopulation, inds, state, thread, true);  // DO produce children from source -- we've not done so already



        GPInitializer initializer = ((GPInitializer)state.initializer);

        for(int q=start;q<n+start; /* no increment */)  // keep on going until we're filled up
            {
            // grab two individuals from our sources
            if (sources[0]==sources[1])  // grab from the same source  always true, even tc0 and tc1 appear
                sources[0].produce(2,2,0,subpopulation,parents,state,thread);  //max start subpopulation
        
            else // grab from different sources
                {
                sources[0].produce(1,1,0,subpopulation,parents,state,thread);
                sources[1].produce(1,1,1,subpopulation,parents,state,thread);
                }

            // at this point, parents[] contains our two selected individuals

            // are our tree values valid?
            if (tree1!=TREE_UNFIXED && (tree1<0 || tree1 >= parents[0].trees.length))
                // uh oh
                state.output.fatal("GP Crossover Pipeline attempted to fix tree.0 to a value which was out of bounds of the array of the individual's trees.  Check the pipeline's fixed tree values -- they may be negative or greater than the number of trees in an individual");
            if (tree2!=TREE_UNFIXED && (tree2<0 || tree2 >= parents[1].trees.length))
                // uh oh
                state.output.fatal("GP Crossover Pipeline attempted to fix tree.1 to a value which was out of bounds of the array of the individual's trees.  Check the pipeline's fixed tree values -- they may be negative or greater than the number of trees in an individual");

            int t1=0; int t2=0;
            if (tree1==TREE_UNFIXED || tree2==TREE_UNFIXED)
                {
                do
                    // pick random trees  -- their GPTreeConstraints must be the same
                    {
                    if (tree1==TREE_UNFIXED)
                    	
                        if (parents[0].trees.length > 1)
                            t1 = state.random[thread].nextInt(parents[0].trees.length);
                        else 
                        	{t1 = 0;
                    //System.out.println(parents[0].trees.length);
                        	}
                    else 
                    	t1 = tree1;

                    if (tree2==TREE_UNFIXED)
                        if (parents[1].trees.length>1)
                            t2 = state.random[thread].nextInt(parents[1].trees.length);
                        else t2 = 0;
                    else 
                    	t2 = tree2;
                    } while (parents[0].trees[t1].constraints(initializer) != parents[1].trees[t2].constraints(initializer));
                }
            else
                {
                t1 = tree1;
                t2 = tree2;
                // make sure the constraints are okay
                if (parents[0].trees[t1].constraints(initializer)
                    != parents[1].trees[t2].constraints(initializer)) // uh oh
                    state.output.fatal("GP Crossover Pipeline's two tree choices are both specified by the user -- but their GPTreeConstraints are not the same");
                }

            // validity results...
            boolean res1 = false;
            boolean res2 = false;

            // prepare the nodeselectors
            nodeselect1.reset();
            nodeselect2.reset();

            // pick some nodes

            GPNode p1=null;
            GPNode p2=null;

            //======================start========================================
            double rnd = state.random[thread].nextDouble(); //probability  (0,1)
            GPIndividual j1 = (GPIndividual)(parents[0].lightClone());
            GPIndividual j2 = null;
            if (n-(q-start)>=2 && !tossSecondParent) j2 = (GPIndividual)(parents[1].lightClone());

            // Fill in various tree information that didn't get filled in there
            j1.trees = new GPTree[parents[0].trees.length];
            if (n-(q-start)>=2 && !tossSecondParent) j2.trees = new GPTree[parents[1].trees.length];

			for (int x = 0; x < numTries; x++) {
				if (rnd < 0.9) {
					// pick a node in individual 1
					p1 = nodeselect1.pickNode(state, subpopulation, thread, parents[0], parents[0].trees[t1]);
					// pick a node in individual 2
					p2 = nodeselect2.pickNode(state, subpopulation, thread, parents[1], parents[1].trees[t2]);
				} else {
					// pick a root node in individual 1
					p1 = nodeselect1.pickRootNode(state, subpopulation, thread, parents[0], parents[0].trees[t1]);
					// pick a root node in individual 2
					p2 = nodeselect2.pickRootNode(state, subpopulation, thread, parents[1], parents[1].trees[t2]);
				}
                 // check for depth and swap-compatibility limits
                 res1 = verifyPoints(initializer,p2,p1);  // p2 can fill p1's spot -- order is important!
                 if (n-(q-start)<2 || tossSecondParent) res2 = true;
                 else res2 = verifyPoints(initializer,p1,p2);  // p1 can fill p2's spot -- order is important!

                 // did we get something that had both nodes verified?
                 // we reject if EITHER of them is invalid.  This is what lil-gp does.
                 // Koza only has numTries set to 1, so it's compatible as well.
                 if (res1 && res2) break;
                 }
                  // at this point, p1 or p2, or both, may be null.
                  // If not, swap one in.  Else just copy the parent.

                  for(int x=0;x<j1.trees.length;x++)
                      {
                      if (x==t1 && res1)  // we've got a tree with a kicking cross position!
                          {
                          j1.trees[x] = (GPTree)(parents[0].trees[x].lightClone());
                          j1.trees[x].owner = j1;
                          j1.trees[x].child = parents[0].trees[x].child.cloneReplacing(p2,p1);
                          j1.trees[x].child.parent = j1.trees[x];
                          j1.trees[x].child.argposition = 0;
                          j1.evaluated = false;
                          }  // it's changed
                      else
                          {
                          j1.trees[x] = (GPTree)(parents[0].trees[x].lightClone());
                          j1.trees[x].owner = j1;
                          j1.trees[x].child = (GPNode)(parents[0].trees[x].child.clone());
                          j1.trees[x].child.parent = j1.trees[x];
                          j1.trees[x].child.argposition = 0;
                          }
                      }

                  if (n-(q-start)>=2 && !tossSecondParent)
                      for(int x=0;x<j2.trees.length;x++)
                          {
                          if (x==t2 && res2)  // we've got a tree with a kicking cross position!
                              {
                              j2.trees[x] = (GPTree)(parents[1].trees[x].lightClone());
                              j2.trees[x].owner = j2;
                              j2.trees[x].child = parents[1].trees[x].child.cloneReplacing(p1,p2);
                              j2.trees[x].child.parent = j2.trees[x];
                              j2.trees[x].child.argposition = 0;
                              j2.evaluated = false;
                              } // it's changed
                          else
                              {
                              j2.trees[x] = (GPTree)(parents[1].trees[x].lightClone());
                              j2.trees[x].owner = j2;
                              j2.trees[x].child = (GPNode)(parents[1].trees[x].child.clone());
                              j2.trees[x].child.parent = j2.trees[x];
                              j2.trees[x].child.argposition = 0;
                              }
                          }
            // add the individuals to the population
            inds[q] = j1;
            q++;
            if (q<n+start && !tossSecondParent) //tossSecondParent: false     !tossSecondParent: true
                {
                inds[q] = j2;
                q++;
                }
            }
        return n;
        }

}
