function [c,r,tangpts,intuzians,ju]=seven_example()
% seven_example() Analyse computations for epecific case
%   Going to get data from CirclePack and compare it to what
%   we compute here.

% original flower data (center is vert 8)
r=[1.513914364,4.807386169,4.064050398,1.490297794,4.314633637,4.413541545,3.845795113,0.8269529508];
c=[-2.3359845515-0.15111572535i,0.94439551488-5.5546281990i,4.0378698659+2.7599856343i,-1.4361169743+1.8185761047i,-3.7694134662-3.4967749087i,4.8336811454-2.0244281035i,4.6727480642i,0.0];

% CirclePack's uzians:
ju=[1.0-0.313472428,1.0-0.240425447,1.0-0.244838195,1.0-0.310490541,1.0-0.240468677,1.0-0.363261352,1.0-0.238416392];

% tangency points of original flower for faces 1 to 7. 
% For each fact, tangencies start with clockwise outword
% spoke, then outer edge, then cclw inward spoke.
tangpts=zeros(7,3);
for j=1:6
    tangpts(j,1)=(r(8)/(r(8)+r(j)))*c(j); % outward spoke
    tangpts(j,2)=c(j)+(r(j)/(r(j)+r(j+1)))*(c(j+1)-c(j)); % outer edge
    tangpts(j,3)=(r(8)/(r(8)+r(j+1)))*c(j+1); % inward spoke
end
tangpts(7,1)=tangpts(6,3);
tangpts(7,2)=c(1)+(r(1)/(r(1)+r(7)))*(c(7)-c(1));
tangpts(7,3)=tangpts(1,1);

% compute aligned base Mobius transformations, b2f and b2g
% and intrinsic schwarzians sfg
intuzians=zeros(1,7);
t1=tangpts(7,1:3);
t2=tangpts(1,1:3);
[b2f,b2g,sfg]=alignFaceMobs(t1,t2);
intuzians(1)=1.0-sfg;
for j=2:6
    t1=tangpts(j-1,1:3);
    t2=tangpts(j,1:3);
    [b2f,b2g,sfg]=alignFaceMobs(t1,t2);
    intuzians(j)=1.0-sfg;
end
end