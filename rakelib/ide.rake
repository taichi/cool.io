
namespace :ide do
  def xml(name, &block)
    require 'builder'
    open(name, 'w') do |file|
      xml = Builder::XmlMarkup.new(:target=>file, :indent=>2)
      xml.instruct!
      block.call xml
    end
  end

  file '.project' do |task|
    xml task.name do |x|
      x.projectDescription do
        x.name 'cool.io'
        x.comment
        x.projects
        x.natures do
          x.nature 'org.eclipse.jdt.core.javanature'
        end
        x.buildSpec do
          x.buildCommand do
            x.name 'org.eclipse.jdt.core.javabuilder'
            x.arguments
          end
        end
        x.linkedResources
      end
    end
  end

  file '.classpath' do |task|
    xml task.name do |x|
      x.classpath do
        x.classpathentry :kind=>'output', :path=>'bin'
        x.classpathentry :kind=>'src', :path=>'ext/cool.io'
        x.classpathentry :kind=>'src', :path=>'spec'
        x.classpathentry :kind=>'con', :path=>'org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-1.8'
        Dir["lib/**/*.jar"].reject { |j| j.end_with? 'coolio_ext.jar'}.each do |p|
          x.classpathentry :kind=>'lib', :exported=>'true', :path=>p
        end
      end
    end
  end

  desc 'setup eclipse environment'
  task :eclipse => ['.project', '.classpath']
end

