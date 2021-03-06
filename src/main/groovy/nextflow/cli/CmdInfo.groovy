/*
 * Copyright (c) 2013-2015, Centre for Genomic Regulation (CRG).
 * Copyright (c) 2013-2015, Paolo Di Tommaso and the respective authors.
 *
 *   This file is part of 'Nextflow'.
 *
 *   Nextflow is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   Nextflow is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with Nextflow.  If not, see <http://www.gnu.org/licenses/>.
 */

package nextflow.cli
import java.lang.management.ManagementFactory
import java.nio.file.spi.FileSystemProvider

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import groovy.transform.CompileStatic
import nextflow.Const
import nextflow.exception.AbortOperationException
import nextflow.scm.AssetManager

/**
 * CLI sub-command INFO
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
@Parameters(commandDescription = "Show the system runtime information")
class CmdInfo extends CmdBase {

    static final NAME = 'info'

    @Parameter(description = 'pipeline name')
    List<String> args

    @Parameter(names='-d',description = 'Show detailed information', arity = 0)
    boolean detailed

    @Parameter(names='-dd', hidden = true, arity = 0)
    boolean moreDetailed

    @Override
    final String getName() { NAME }

    @Override
    void run() {
        if( !args ) {
            int level = moreDetailed ? 2 : ( detailed ? 1 : 0 )
            println getInfo(level)
            return
        }

        def repo = new AssetManager().resolveName(args[0])
        if( !repo ) {
            throw new AbortOperationException("Unknown pipeline '${args[0]}'")
        }

        def manager = new AssetManager(repo)
        if( !manager.isLocal() )
            throw new AbortOperationException("Unknown pipeline '${args[0]}'")

        println " repo name  : ${manager.pipeline}"
        println " home page  : ${manager.homePage}"
        println " local path : ${manager.localPath}"
        println " main script: ${manager.mainScriptName}"
        if( manager.description )
        println " description: ${manager.description}"

        def revs = manager.getRevisions()
        if( revs.size() == 1 )
            println " revision   : ${revs[0]}"
        else {
            println " revisions  : "
            revs.each { println " $it" }
        }
    }

    final static private BLANK = '  '
    final static private NEWLINE = '\n'

    static String status(boolean detailed = false) {
        getInfo( detailed ? 1 : 0, true )
    }

    /**
     * @return A string containing some system runtime information
     */
    static String getInfo(int level, boolean printProc=false) {

        def props = System.getProperties()
        def result = new StringBuilder()
        result << BLANK << "Version: ${Const.APP_VER} build ${Const.APP_BUILDNUM}" << NEWLINE
        result << BLANK << "Modified: ${Const.APP_TIMESTAMP_UTC} ${Const.deltaLocal()}" << NEWLINE
        result << BLANK << "System: ${props['os.name']} ${props['os.version']}" << NEWLINE
        result << BLANK << "Runtime: Groovy ${GroovySystem.getVersion()} on ${System.getProperty('java.vm.name')} ${props['java.runtime.version']}" << NEWLINE
        result << BLANK << "Encoding: ${System.getProperty('file.encoding')} (${System.getProperty('sun.jnu.encoding')})" << NEWLINE

        if( printProc )
            result << BLANK << "Process: ${ManagementFactory.getRuntimeMXBean().getName()} " << getLocalAddress() << NEWLINE

        if( level == 0  )
            return result.toString()

        List<String> capsule = []
        List<String> args = []
        ManagementFactory
                .getRuntimeMXBean()
                .getInputArguments()
                .each { String it ->
                        if( it.startsWith('-Dcapsule.'))
                            capsule << it.substring(2)
                        else
                            args << it
                    }

        // file system
        result << BLANK << "File systems: "
        result << FileSystemProvider.installedProviders().collect { it.scheme }.join(', ')
        result << NEWLINE

        // JVM options
        result << BLANK << "JVM opts:" << NEWLINE
        for( String entry : args ) {
            int p = entry.indexOf('=')
            if( p!=-1 ) {
                def key = entry.substring(0,p)
                def value = entry.substring(p+1)
                dump(key,value,2, result)
            }
            else {
                dump(entry,null,2, result)
            }
        }

        // Capsule options
        dump("Capsule", capsule, 1, result)

        // Env
        result << BLANK << "Environment:" << NEWLINE
        def entries = System.getenv().keySet().sort()
        for( def key : entries ) {
            if( key.startsWith('NXF_') || level>1 ) {
                dump(  key, System.getenv().get(key), 2, result )
            }
        }

        // java properties
        if( level>1 ) {
            result << BLANK << "Properties:" << NEWLINE
            entries = System.getProperties().keySet().sort()
            for( String key : entries ) {
                if( key == 'java.class.path' ) continue
                dump( key, System.getProperties().get(key), 2, result )
            }
        }

        // Class path
        dump("Class-path" , System.getProperty('java.class.path'), 1, result)

        // final string
        return result.toString()
    }

    static private void dump(String key, def value, int indent, StringBuilder result) {

        if( value instanceof String && value.count(':') && (key=~/.*PATH$/ || key=='PERL5LIB' || key.contains('.path') || key.contains('-path') || key.contains('.dir')) ) {
            value = value.split(":") as Collection
        }
        else if( value?.class?.isArray() ) {
            value = value as Collection
        }

        if ( value instanceof Collection ) {
            blanks(indent, result)
            result << key << (indent==1 ? ':' : '=')
            for( String item : value ) {
                result << NEWLINE
                blanks(indent+1, result)
                result << item
            }
        }
        else if( value ) {
            blanks(indent, result)
            result << key << (indent==1 ? ':' : '=')
            if( value == '\n' ) {
                result << '\\n'
            }
            else if( value == '\r' ) {
                result << '\\r'
            }
            else if( value == '\n\r' ) {
                result << '\\n\\r'
            }
            else if( value == '\r\n' ) {
                result << '\\r\\n'
            }
            else {
                result << value
            }
        }
        else if( key ) {
            blanks(indent, result)
            result << key
        }
        result << NEWLINE
    }

    static private blanks( int n, StringBuilder result ) {
        for( int i=0; i<n; i++ ) {
            result << BLANK
        }
    }

    /**
     * @return A string holding the local host name and address used for logging
     */
    static private getLocalAddress() {
        try {
            return "[${InetAddress.getLocalHost().getHostAddress()}]"
        }
        catch(Exception e) {
            return null
        }
    }
}
