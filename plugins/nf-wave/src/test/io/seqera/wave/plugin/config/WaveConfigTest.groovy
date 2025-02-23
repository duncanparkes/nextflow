/*
 * Copyright 2013-2023, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.seqera.wave.plugin.config

import nextflow.util.Duration
import spock.lang.Specification
import spock.lang.Unroll

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class WaveConfigTest extends Specification {

    def 'should create empty opts' () {
        when:
        def opts = new WaveConfig([:])
        then:
        !opts.enabled()
        opts.endpoint() == 'https://wave.seqera.io'
    }

    def 'should create from env' () {
        given:
        def ENV = [WAVE_API_ENDPOINT: 'http://foo']
        when:
        def opts = new WaveConfig([:], ENV)
        then:
        !opts.enabled()
        opts.endpoint() == 'http://foo'
    }

    def 'should create config options' () {
        given:
        def ENV = [WAVE_API_ENDPOINT: 'http://foo']
        when:
        // config options have priority over sys env
        def opts = new WaveConfig([enabled:true, endpoint: 'http://localhost'], ENV)
        then:
        opts.enabled()
        opts.endpoint() == 'http://localhost'
    }

    def 'should remove ending slash' () {
        when:
        def opts = new WaveConfig([enabled:true, endpoint: 'http://localhost/v1//'])
        then:
        opts.enabled()
        opts.endpoint() == 'http://localhost/v1'
    }

    @Unroll
    def 'should add config urls'  () {
        when:
        def opts = new WaveConfig(OPTS, ENV)
        then:
        opts.containerConfigUrl() == EXPECTED

        where:
        OPTS                                                        | ENV                                                   | EXPECTED
        [:]                                                         | [:]                                                   | []
        [containerConfigUrl: 'http://foo.com']                      | [:]                                                   | [ new URL('http://foo.com')]
        [containerConfigUrl: 'http://foo.com']                      | [WAVE_CONTAINER_CONFIG_URL:'http://something.com']    | [ new URL('http://foo.com')]
        [:]                                                         | [WAVE_CONTAINER_CONFIG_URL:'http://something.com']    | [ new URL('http://something.com')]
        [containerConfigUrl: ['http://foo.com','https://bar.com']]  | [:]                                                   | [ new URL('http://foo.com'), new URL('https://bar.com')]
        [containerConfigUrl: ['http://foo.com','https://bar.com']]  | [WAVE_CONTAINER_CONFIG_URL:'http://boo.com']          | [ new URL('http://foo.com'), new URL('https://bar.com')]

    }

    def 'should get conda config' () {
        when:
        def opts = new WaveConfig([:])
        then:
        opts.condaOpts().mambaImage == 'mambaorg/micromamba:1.4.2'
        opts.condaOpts().commands == null

        when:
        opts = new WaveConfig([build:[conda:[mambaImage:'mambaorg/foo:1', commands:['USER hola']]]])
        then:
        opts.condaOpts().mambaImage == 'mambaorg/foo:1'
        opts.condaOpts().commands == ['USER hola']
        
    }

    def 'should get spack config' () {
        when:
        def opts = new WaveConfig([:])
        then:
        opts.spackOpts().basePackages == null
        opts.spackOpts().commands == null

        when:
        opts = new WaveConfig([build:[spack:[ basePackages: 'foo bar', commands:['USER hola'] ]]])
        then:
        opts.spackOpts().basePackages == 'foo bar'
        opts.spackOpts().commands == ['USER hola']
        
    }

    def 'should get build and cache repos' () {
        when:
        def opts = new WaveConfig([:])
        then:
        opts.buildRepository() == null
        opts.cacheRepository() == null

        when:
        opts = new WaveConfig([build:[repository:'some/repo', cacheRepository:'some/cache']])
        then:
        opts.buildRepository() == 'some/repo'
        opts.cacheRepository() == 'some/cache'
    }

    @Unroll
    def 'should set strategy' () {
        when:
        def opts = new WaveConfig([:])
        then:
        opts.strategy() == ['container','dockerfile','conda','spack']

        when:
        opts = new WaveConfig([strategy:STRATEGY])
        then:
        opts.strategy() == EXPECTED

        where:
        STRATEGY                | EXPECTED
        null                    | ['container','dockerfile','conda','spack']
        'dockerfile'            | ['dockerfile']
        'conda,container'       | ['conda','container']
        'conda , container'     | ['conda','container']
        ['conda','container']   | ['conda','container']
        [' conda',' container'] | ['conda','container']
        'spack'                 | ['spack']
    }

    def 'should fail to set strategy' () {
        when:
        new WaveConfig([strategy:['foo']])
        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Invalid value for 'wave.strategy' configuration attribute - offending value: foo"
    }

    def 'should get retry policy' () {
        when:
        def opts = new WaveConfig([:])
        then:
        opts.retryOpts().maxAttempts == 5
        opts.retryOpts().maxDelay == Duration.of('90s')

        when:
        opts = new WaveConfig([retryPolicy:[ maxAttempts: 20, jitter: 1.0, delay: '1s', maxDelay: '10s' ]])
        then:
        opts.retryOpts().maxAttempts == 20
        opts.retryOpts().jitter == 1.0d
        opts.retryOpts().delay == Duration.of('1s')
        opts.retryOpts().maxDelay == Duration.of('10s')

        // legacy
        when:
        opts = new WaveConfig([retry:[ maxAttempts: 10, jitter: 2.0, delay: '3s', maxDelay: '40s' ]])
        then:
        opts.retryOpts().maxAttempts == 10
        opts.retryOpts().jitter == 2.0d
        opts.retryOpts().delay == Duration.of('3s')
        opts.retryOpts().maxDelay == Duration.of('40s')
    }

    def 'should get http config options' () {
        when:
        def opts = new WaveConfig([:])
        then:
        opts.httpOpts().connectTimeout() == java.time.Duration.ofSeconds(30)

        when:
        opts = new WaveConfig([httpClient: [connectTimeout: '90s']])
        then:
        opts.httpOpts().connectTimeout() == java.time.Duration.ofSeconds(90)
    }
}
