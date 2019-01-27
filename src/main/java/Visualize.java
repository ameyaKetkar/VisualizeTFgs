import static com.google.errorprone.bugpatterns.T2R.Analysis.Analysis.induceDisconnectedSubgraphs1;
import static com.google.errorprone.bugpatterns.T2R.Analysis.GenerateRefactorables.matchProgram;
import static com.google.errorprone.bugpatterns.T2R.Analysis.PreConditions.NO_INFERRED_ASSIGNMENT;
import static com.google.errorprone.bugpatterns.T2R.Analysis.PreConditions.NO_INFERRED_METHOD_IN_HIERARCHY;
import static com.google.errorprone.bugpatterns.T2R.Analysis.PreConditions.NO_INFERRED_PASSED_AS_ARG;
import static com.google.errorprone.bugpatterns.T2R.common.TypeFactGraph.emptyTFG;
import static com.google.errorprone.bugpatterns.T2R.common.TypeFactGraph.induceGraph;
import static com.google.errorprone.bugpatterns.T2R.common.Util.Pair.P;
import static com.google.errorprone.bugpatterns.T2R.common.Visualizer.prettyType;
import static com.google.errorprone.bugpatterns.T2R.common.Visualizer.qualifiedName;
import static guru.nidi.graphviz.model.Factory.graph;
import static guru.nidi.graphviz.model.Factory.node;
import static guru.nidi.graphviz.model.Factory.to;
import static java.util.stream.Collectors.toList;

import com.google.errorprone.bugpatterns.T2R.Analysis.Analysis;
import com.google.errorprone.bugpatterns.T2R.Analysis.Migrate;
import com.google.errorprone.bugpatterns.T2R.common.Models.IdentificationOuterClass.Identification;
import com.google.errorprone.bugpatterns.T2R.common.Models.TFGOuterClass.TFG;
import com.google.errorprone.bugpatterns.T2R.common.Models.TFGOuterClass.TFG.Edge;
import com.google.errorprone.bugpatterns.T2R.common.RWProtos;
import com.google.errorprone.bugpatterns.T2R.common.TypeFactGraph;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import guru.nidi.graphviz.attribute.Label;
import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
import guru.nidi.graphviz.model.Graph;
import guru.nidi.graphviz.model.Link;
import guru.nidi.graphviz.model.Node;

public class Visualize {

    public static void main(String a[]){
        final List<TFG> tfgss = RWProtos.readTFG(a[0]);

        final TypeFactGraph<Identification> gTFG = tfgss.stream().map(TypeFactGraph::of).reduce(Analysis::merge).orElse(emptyTFG());

        final TypeFactGraph<Identification> globalTFG =
                UnaryOperator.<TypeFactGraph<Identification>>identity()
                        .andThen(Analysis::simplifyMethods)
                        .andThen(Analysis::resolveInferred)
                        .andThen(Analysis::paramArgRelation)
                        .andThen(Analysis::resolveSuperClause)
                        .andThen(Analysis::removeNonPvt)
                        .andThen(Analysis::propogateAffectedByHierarchy)
                        .andThen(Analysis::removeParentParam)
                        .apply(gTFG);

        final List<TypeFactGraph<Identification>> globalTFGs = induceDisconnectedSubgraphs1(globalTFG).stream()
                .filter(x->matchProgram(x, Migrate.mapping).isPresent())
                .map(x -> induceGraph(globalTFG, x)).filter(x -> !x.isEmpty())
                .filter(NO_INFERRED_PASSED_AS_ARG)
                .filter(NO_INFERRED_ASSIGNMENT)
                .filter(NO_INFERRED_METHOD_IN_HIERARCHY)
                .collect(toList());

        visualizeGraph(a[0], globalTFGs.stream().map(TypeFactGraph::asTFG).collect(toList()));


    }

    public static void visualizeGraph(String path, List<TFG> tfg) {

        for(int i = 0; i < tfg.size(); i++) {
            final List<Node> ns = generateEdge(tfg.get(i));
            Node[] t = ns.toArray(new Node[ns.size()]);
            Graph g = graph().directed().with(t);
            try {
                Graphviz.fromGraph(g).height(200).width(100).render(Format.SVG).toFile(new File(path + "TFG" + i +".svg"));
            } catch (IOException e) {
                System.out.println(e.toString());
                e.printStackTrace();
            }
        }
    }

    private static Node getNode(Identification id){
        if(!id.hasOwner())
            return  node(id.getName() + "\n" + id.getKind());
        return node(id.getName() + "\n" + id.getKind() + "\n" + prettyType(id.getType()) + "\n" + qualifiedName(id)+ "\n"+  id.getOwner().hashCode());
    }

    private static List<Node> generateEdge(TFG tfg){
        final List<Identification> ns = tfg.getNodesList();
        final Map<Integer, List<Edge>> g = tfg.getEdgesList().stream().collect(Collectors.groupingBy(x -> x.getFst()));
        return g.entrySet().stream()
                .map(e -> P(getNode(ns.get(e.getKey())),
                        e.getValue().stream().map(x -> to(getNode(ns.get(x.getSnd()))).with(Label.of(x.getEdgeValue()))).collect(toList())))
                .map(p -> p.fst().link(p.snd().toArray(new Link[p.snd().size()]))).collect(toList());
    }

}
