package braindustry.annotations.RemoteProc;

import arc.struct.Seq;
import arc.util.io.ReusableByteOutStream;
import arc.util.io.Writes;
import com.squareup.javapoet.*;
import mindustry.annotations.Annotations;
import mindustry.annotations.BaseProcessor;
import mindustry.annotations.remote.ClassEntry;
import mindustry.annotations.remote.MethodEntry;
import mindustry.annotations.remote.RemoteProcess;
import mindustry.annotations.remote.SerializerResolver;
import mindustry.annotations.util.TypeIOResolver;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.io.DataOutputStream;
import java.io.IOException;

public class ModRemoteWriteGenerator {
    private final TypeIOResolver.ClassSerializer serializers;

    /**
     * Creates a write generator that uses the supplied serializer setup.
     */
    public ModRemoteWriteGenerator(TypeIOResolver.ClassSerializer serializers) {
        this.serializers = serializers;
    }
    /**
     * Generates convToStr method
     * */
    private MethodSpec.Builder createConvToStrMethod() {
        MethodSpec.Builder method = MethodSpec.methodBuilder("convToStr")
                .addParameter(byte[].class,"bytes")
                .addModifiers(Modifier.STATIC)
                .returns(String.class);
//        method.addStatement("return new String(java.util.Base64.getEncoder().encode(bytes))");

//        method.addStatement("arc.util.Log.info(\"line: [@] lenght: @ @\",new String(bytes),new String(bytes).length(),bytes.length);");
        method.addStatement("return new String(bytes)");
        return method;
    }

    /**
     * Generates all classes in this list.
     */
    public void generateFor(Seq<ClassEntry> entries, String packageName) throws IOException {
        for (ClassEntry entry : entries) {
            //create builder
            TypeSpec.Builder classBuilder = TypeSpec.classBuilder(entry.name).addModifiers(Modifier.PUBLIC);
            classBuilder.addMethod(createConvToStrMethod().build());
            classBuilder.addJavadoc(RemoteProcess.autogenWarning);

            //add temporary write buffer
            classBuilder.addField(FieldSpec.builder(ReusableByteOutStream.class, "OUT", Modifier.STATIC, Modifier.PRIVATE, Modifier.FINAL)
                    .initializer("new ReusableByteOutStream($L)", RemoteProcess.maxPacketSize).build());

            //add writer for that buffer
            classBuilder.addField(FieldSpec.builder(Writes.class, "WRITE", Modifier.STATIC, Modifier.PRIVATE, Modifier.FINAL)
                    .initializer("new Writes(new $T(OUT))", DataOutputStream.class).build());

            //go through each method entry in this class
            for (MethodEntry methodEntry : entry.methods) {
                //write the 'send event to all players' variant: always happens for clients, but only happens if 'all' is enabled on the server method
                if (methodEntry.where.isClient || methodEntry.target.isAll) {
                    writeMethodVariant(classBuilder, methodEntry, true, false);
                }

                //write the 'send event to one player' variant, which is only applicable on the server
                if (methodEntry.where.isServer && methodEntry.target.isOne) {
                    writeMethodVariant(classBuilder, methodEntry, false, false);
                }

                //write the forwarded method version
                if (methodEntry.where.isServer && methodEntry.forward) {
                    writeMethodVariant(classBuilder, methodEntry, true, true);
                }
            }

            //build and write resulting class
            TypeSpec spec = classBuilder.build();
            JavaFile.builder(packageName, spec).build().writeTo(BaseProcessor.filer);
        }
    }



    /**
     * Creates a specific variant for a method entry.
     */
    private void writeMethodVariant(TypeSpec.Builder classBuilder, MethodEntry methodEntry, boolean toAll, boolean forwarded) {
        ExecutableElement elem = methodEntry.element;

        //create builder
        String methodName = elem.getSimpleName().toString() + (forwarded ? "__forward" : "");
        MethodSpec.Builder method = MethodSpec.methodBuilder(methodName) //add except suffix when forwarding
                .addModifiers(Modifier.STATIC)
                .returns(void.class);

        //forwarded methods aren't intended for use, and are not public
        if (!forwarded) {
            method.addModifiers(Modifier.PUBLIC);
        }

        //validate client methods to make sure
        if (methodEntry.where.isClient) {
            if (elem.getParameters().isEmpty()) {
                BaseProcessor.err("Client invoke methods must have a first parameter of type Player", elem);
                return;
            }

            if (!elem.getParameters().get(0).asType().toString().contains("Player")) {
                BaseProcessor.err("Client invoke methods should have a first parameter of type Player", elem);
                return;
            }
        }

        //if toAll is false, it's a 'send to one player' variant, so add the player as a parameter
        if (!toAll) {
            method.addParameter(ClassName.bestGuess("mindustry.net.NetConnection"), "playerConnection");
        }

        //add sender to ignore
        if (forwarded) {
            method.addParameter(ClassName.bestGuess("mindustry.net.NetConnection"), "exceptConnection");
        }

        //call local method if applicable, shouldn't happen when forwarding method as that already happens by default
        if (!forwarded && methodEntry.local != Annotations.Loc.none) {
            //add in local checks
            if (methodEntry.local != Annotations.Loc.both) {
                method.beginControlFlow("if(" + getCheckString(methodEntry.local) + " || !mindustry.Vars.net.active())");
            }

            //concatenate parameters
            int index = 0;
            StringBuilder results = new StringBuilder();
            for (VariableElement var : elem.getParameters()) {
                //special case: calling local-only methods uses the local player
                if (index == 0 && methodEntry.where == Annotations.Loc.client) {
                    results.append("mindustry.Vars.player");
                } else {
                    results.append(var.getSimpleName());
                }
                if (index != elem.getParameters().size() - 1) results.append(", ");
                index++;
            }

            //add the statement to call it
            method.addStatement("$N." + elem.getSimpleName() + "(" + results.toString() + ")",
                    ((TypeElement) elem.getEnclosingElement()).getQualifiedName().toString());

            if (methodEntry.local != Annotations.Loc.both) {
                method.endControlFlow();
            }
        }

        //start control flow to check if it's a
        // ctually client/server so no netcode is called
        method.beginControlFlow("if(" + getCheckString(methodEntry.where) + ")");
        //reset stream
        method.addStatement("OUT.reset()");
        //assign method ID
        method.addStatement("WRITE.b(" + methodEntry.id+")");

        method.addTypeVariables(Seq.with(elem.getTypeParameters()).map(BaseProcessor::getTVN));

        for (int i = 0; i < elem.getParameters().size(); i++) {
            //first argument is skipped as it is always the player caller
            if ((!methodEntry.where.isServer/* || methodEntry.mode == Loc.both*/) && i == 0) {
                continue;
            }

            VariableElement var = elem.getParameters().get(i);

            try {
                //add parameter to method
                method.addParameter(TypeName.get(var.asType()), var.getSimpleName().toString());
            } catch (Throwable t) {
                throw new RuntimeException("Error parsing method " + methodEntry.targetMethod);
            }

            //name of parameter
            String varName = var.getSimpleName().toString();
            //name of parameter type
            String typeName = var.asType().toString();
            //captialized version of type name for writing primitives
            String capName = typeName.equals("byte") ? "" : Character.toUpperCase(typeName.charAt(0)) + typeName.substring(1);
            //special case: method can be called from anywhere to anywhere
            //thus, only write the player when the SERVER is writing data, since the client is the only one who reads it
            boolean writePlayerSkipCheck = methodEntry.where == Annotations.Loc.both && i == 0;

            if (writePlayerSkipCheck) { //write begin check
                method.beginControlFlow("if(mindustry.Vars.net.server())");
            }

            if (BaseProcessor.isPrimitive(typeName)) { //check if it's a primitive, and if so write it
                method.addStatement("WRITE.$L($L)", typeName.equals("boolean") ? "bool" : typeName.charAt(0) + "", varName);
            } else {
                //else, try and find a serializer
                String ser = serializers.writers.get(typeName.replace("mindustry.gen.", ""), SerializerResolver.locate(elem, var.asType(), true));

                if (ser == null) { //make sure a serializer exists!
                    BaseProcessor.err("No @WriteClass method to write class type: '" + typeName + "'", var);
                    return;
                }

                //add statement for writing it
                method.addStatement(ser + "(WRITE, " + varName + ")");
            }

            if (writePlayerSkipCheck) { //write end check
                method.endControlFlow();
            }
        }
        //anuke's code
        if (false) {
            //assign packet bytes
            method.addStatement("packet.bytes = OUT.getBytes()");
            //assign packet length
            method.addStatement("packet.length = OUT.size()");

            String sendString;

            if (forwarded) { //forward packet
                if (!methodEntry.local.isClient) { //if the client doesn't get it called locally, forward it back after validation
                    sendString = "mindustry.Vars.net.send(";
                } else {
                    sendString = "mindustry.Vars.net.sendExcept(exceptConnection, ";
                }
            } else if (toAll) { //send to all players / to server
                sendString = "mindustry.Vars.net.send(";
            } else { //send to specific client from server
                sendString = "playerConnection.send(";
            }

            //send the actual packet
            method.addStatement(sendString + "packet, " +
                                (methodEntry.unreliable ? "mindustry.net.Net.SendMode.udp" : "mindustry.net.Net.SendMode.tcp") + ")");


            //end check for server/client
            method.endControlFlow();
        } else{

            String sendStringServer,sendStringClient;
            String func = "PacketUnreliable";
            if (forwarded) { //forward packet
                if (!methodEntry.local.isClient) { //if the client doesn't get it called locally, forward it back after validation
                    sendStringServer = "mindustry.gen.Call.client" + func + "(ModVars.modVars.braindustryPacketPrefixClient,str)";
                    sendStringClient = "mindustry.gen.Call.server" + func + "(ModVars.modVars.braindustryPacketPrefixServer,str)";
                } else {
                    sendStringServer = "ModVars.modFunc.client" + func + "Except(exceptConnection,ModVars.modVars.braindustryPacketPrefixClient,str)";
                    sendStringClient = "ModVars.modFunc.server" + func + "Except(exceptConnection,ModVars.modVars.braindustryPacketPrefixServer,str)";
                }
            } else if (toAll) { //send to all players / to server
                sendStringServer = "mindustry.gen.Call.client" + func + "(ModVars.modVars.braindustryPacketPrefixClient,str)";
                sendStringClient = "mindustry.gen.Call.server" + func + "(ModVars.modVars.braindustryPacketPrefixClient,str)";
            } else { //send to specific client from server
                sendStringServer = "mindustry.gen.Call.client" + func + "(playerConnection,ModVars.modVars.braindustryPacketPrefixClient,str)";
                sendStringClient = "mindustry.gen.Call.server" + func + "(ModVars.modVars.braindustryPacketPrefixServer,str)";
            }
            method.addStatement("String str=convToStr(OUT.toByteArray())");
//            method.addStatement("arc.util.Log.info(\"ModCall."+methodName+"\")");
            method.beginControlFlow("if (mindustry.Vars.net.client())");
            method.addStatement(sendStringClient);
            method.endControlFlow("else { "+sendStringServer+";}");


            //end check for server/client
            method.endControlFlow();
        }

        //add method to class, finally
        classBuilder.addMethod(method.build());
    }

    private String getCheckString(Annotations.Loc loc) {
        return loc.isClient && loc.isServer ? "mindustry.Vars.net.server() || mindustry.Vars.net.client()" :
                loc.isClient ? "mindustry.Vars.net.client()" :
                        loc.isServer ? "mindustry.Vars.net.server()" : "false";
    }
}
