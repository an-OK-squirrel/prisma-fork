package com.puzzletimer.puzzles;

import java.awt.Color;
import java.util.HashMap;

import com.puzzletimer.graphics.Mesh;
import com.puzzletimer.graphics.algebra.Matrix44;
import com.puzzletimer.graphics.geometry.Plane;
import com.puzzletimer.models.ColorScheme;
import com.puzzletimer.models.PuzzleInfo;

public class Pyraminx implements Puzzle {
    @Override
    public PuzzleInfo getPuzzleInfo() {
        return new PuzzleInfo("PYRAMINX");
    }

    @Override
    public String toString() {
        return getPuzzleInfo().getDescription();
    }

    private static class Twist {
        public Plane plane;
        public double angle;

        public Twist(Plane plane, double angle) {
            this.plane = plane;
            this.angle = angle;
        }
    }

    @Override
    public Mesh getScrambledPuzzleMesh(ColorScheme colorScheme, String[] sequence) {
        Color[] colorArray = {
            colorScheme.getFaceColor("FACE-D").getColor(),
            colorScheme.getFaceColor("FACE-L").getColor(),
            colorScheme.getFaceColor("FACE-R").getColor(),
            colorScheme.getFaceColor("FACE-F").getColor(),
        };

        Mesh mesh = Mesh.tetrahedron(colorArray);

        double h1 = Math.sqrt(8d) / 3d * (Math.sqrt(3d) / 2d * 1.5d);

        Plane plane1 = Plane.fromVectors(
            mesh.vertices.get(mesh.faces.get(0).vertexIndices.get(0)),
            mesh.vertices.get(mesh.faces.get(0).vertexIndices.get(1)),
            mesh.vertices.get(mesh.faces.get(0).vertexIndices.get(2)));

        Plane planeu = new Plane(plane1.p.sub(plane1.n.mul(2d * h1 / 3d)), plane1.n.neg());
        Plane planeU = new Plane(plane1.p.sub(plane1.n.mul(h1 / 3d)), plane1.n.neg());

        Plane plane2 = Plane.fromVectors(
            mesh.vertices.get(mesh.faces.get(1).vertexIndices.get(0)),
            mesh.vertices.get(mesh.faces.get(1).vertexIndices.get(1)),
            mesh.vertices.get(mesh.faces.get(1).vertexIndices.get(2)));

        Plane planer = new Plane(plane2.p.sub(plane2.n.mul(2d * h1 / 3d)), plane2.n.neg());
        Plane planeR = new Plane(plane2.p.sub(plane2.n.mul(h1 / 3d)), plane2.n.neg());

        Plane plane3 = Plane.fromVectors(
            mesh.vertices.get(mesh.faces.get(2).vertexIndices.get(0)),
            mesh.vertices.get(mesh.faces.get(2).vertexIndices.get(1)),
            mesh.vertices.get(mesh.faces.get(2).vertexIndices.get(2)));

        Plane planel = new Plane(plane3.p.sub(plane3.n.mul(2d * h1 / 3d)), plane3.n.neg());
        Plane planeL = new Plane(plane3.p.sub(plane3.n.mul(h1 / 3d)), plane3.n.neg());

        Plane plane4 = Plane.fromVectors(
            mesh.vertices.get(mesh.faces.get(3).vertexIndices.get(0)),
            mesh.vertices.get(mesh.faces.get(3).vertexIndices.get(1)),
            mesh.vertices.get(mesh.faces.get(3).vertexIndices.get(2)));

        Plane planeb = new Plane(plane4.p.sub(plane4.n.mul(2d * h1 / 3d)), plane4.n.neg());
        Plane planeB = new Plane(plane4.p.sub(plane4.n.mul(h1 / 3d)), plane4.n.neg());

        mesh = mesh
            .shortenFaces(0.08)
            .cut(planeu, 0.05)
            .cut(planeU, 0.05)
            .cut(planer, 0.05)
            .cut(planeR, 0.05)
            .cut(planel, 0.05)
            .cut(planeL, 0.05)
            .cut(planeb, 0.05)
            .cut(planeB, 0.05)
            .softenFaces(0.02)
            .softenFaces(0.01);

        HashMap<String, Twist> twists = new HashMap<String, Twist>();
        twists.put("U",  new Twist(planeU,  2 * Math.PI / 3));
        twists.put("U'", new Twist(planeU, -2 * Math.PI / 3));
        twists.put("u",  new Twist(planeu,  2 * Math.PI / 3));
        twists.put("u'", new Twist(planeu, -2 * Math.PI / 3));
        twists.put("L",  new Twist(planeL,  2 * Math.PI / 3));
        twists.put("L'", new Twist(planeL, -2 * Math.PI / 3));
        twists.put("l",  new Twist(planel,  2 * Math.PI / 3));
        twists.put("l'", new Twist(planel, -2 * Math.PI / 3));
        twists.put("R",  new Twist(planeR,  2 * Math.PI / 3));
        twists.put("R'", new Twist(planeR, -2 * Math.PI / 3));
        twists.put("r",  new Twist(planer,  2 * Math.PI / 3));
        twists.put("r'", new Twist(planer, -2 * Math.PI / 3));
        twists.put("B",  new Twist(planeB,  2 * Math.PI / 3));
        twists.put("B'", new Twist(planeB, -2 * Math.PI / 3));
        twists.put("b",  new Twist(planeb,  2 * Math.PI / 3));
        twists.put("b'", new Twist(planeb, -2 * Math.PI / 3));

        for (String move : sequence) {
            Twist t = twists.get(move);
            mesh = mesh.transformHalfspace(
                Matrix44.rotation(t.plane.n, t.angle),
                t.plane);
        }

        return mesh
            .transform(Matrix44.rotationY(-Math.PI / 4))
            .transform(Matrix44.rotationX(Math.PI / 6));
    }
}
